/*
 *
 * Copyright 2016 CUBRID Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

/*
 * authenticate_owner.cpp - changing owner opertion
 */

#include "authenticate.h"

#include "db.h"
#include "dbtype.h"
#include "transform.h"
#include "object_accessor.h"
#include "execute_statement.h" /* do_get_serial_obj_id */
#include "execute_schema.h"
#include "jsp_cl.h"
#include "trigger_manager.h"
#include "transaction_cl.h"
#include "schema_manager.h" /* sm_downcase_name */
#include "object_primitive.h" /* pr_clear_value */
#include "authenticate_access_auth.hpp" /* au_object_revoke_all_privileges */

int
au_check_owner (DB_VALUE *creator_val)
{
  MOP creator;
  DB_SET *groups;
  int ret_val = ER_FAILED;

  creator = db_get_object (creator_val);

  if (ws_is_same_object (creator, Au_user) || au_is_dba_group_member (Au_user))
    {
      ret_val = NO_ERROR;
    }
  else if (au_get_set (Au_user, "groups", &groups) == NO_ERROR)
    {
      if (set_ismember (groups, creator_val))
	{
	  ret_val = NO_ERROR;
	}
      set_free (groups);
    }

  return ret_val;
}

/*
 * au_change_serial_owner() - Change serial object's owner
 *   return: error code
 *   object(in/out): serial object whose owner is to be changed
 *   new_owner(in): new owner
 *   is_auto_increment(in): check if auto increment serial name change is necessary
 */
int
au_change_serial_owner (MOP serial_mop, MOP owner_mop, bool by_class_owner_change)
{
  DB_OBJECT *serial_owner_obj = NULL;
  DB_OBJECT *serial_class_mop = NULL;
  DB_OBJECT *serial_obj = NULL;
  DB_IDENTIFIER serial_obj_id;
  DB_OTMPL *obj_tmpl = NULL;
  DB_VALUE value;
  const char *serial_name = NULL;
  char serial_new_name[DB_MAX_SERIAL_NAME_LENGTH] = { '\0' };
  const char *att_name = NULL;
  char *owner_name = NULL;
  char downcase_owner_name[DB_MAX_USER_LENGTH] = { '\0' };
  bool is_abort = false;
  int save = 0;
  int error = NO_ERROR;

  if (!serial_mop || !owner_mop)
    {
      ERROR_SET_WARNING (error, ER_OBJ_INVALID_ARGUMENTS);
      return error;
    }

  OID_SET_NULL (&serial_obj_id);

  if (!au_is_dba_group_member (Au_user))
    {
      ERROR_SET_WARNING_1ARG (error, ER_AU_DBA_ONLY, "change_serial_owner");
      return error;
    }

  AU_DISABLE (save);

  /*
   * class, serial, and trigger distinguish user schema by unique_name (user_specified_name).
   * so if the owner of class, serial, trigger changes, the unique_name must also change.
   */

  /*
   * after serial.next_value, the currect value maybe changed, but cub_cas
   * still hold the old value. To get the new value. we need decache it
   * then refetch it from server again.
   */
  assert (WS_ISDIRTY (serial_mop) == false);

  ws_decache (serial_mop);

  /* no need to get last version for serial - actually, the purpose is AU_FETCH_WRITE, so fetch type is not relevant;
   * the last version will be locked and it will be considered visibile only if delid is not set */
  error = au_fetch_instance_force (serial_mop, NULL, AU_FETCH_WRITE, LC_FETCH_MVCC_VERSION);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }

  if (!by_class_owner_change)
    {
      /* It can be checked as one of unique_name, class_name, and att_name. */
      error = obj_get (serial_mop, SERIAL_ATTR_ATTR_NAME, &value);
      if (error != NO_ERROR)
	{
	  ASSERT_ERROR ();
	  goto end;
	}

      if (!DB_IS_NULL (&value))
	{
	  ERROR_SET_WARNING (error, ER_AU_CANT_ALTER_OWNER_OF_AUTO_INCREMENT);
	  goto end;
	}
    }

  /* Check if the owner to be changed is the same. */
  error = obj_get (serial_mop, SERIAL_ATTR_OWNER, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }

  if (DB_VALUE_DOMAIN_TYPE (&value) != DB_TYPE_OBJECT || (serial_owner_obj = db_get_object (&value)) == NULL)
    {
      /* Unable to get attribute value. */
      ERROR_SET_WARNING (error, ER_OBJ_INVALID_ARGUMENTS);
      goto end;
    }

  if (ws_is_same_object (serial_owner_obj, owner_mop))
    {
      goto end;
    }

  error = obj_get (serial_mop, SERIAL_ATTR_NAME, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }

  if (!DB_IS_STRING (&value) || (serial_name = db_get_string (&value)) == NULL)
    {
      /* Unable to get attribute value. */
      ERROR_SET_WARNING (error, ER_OBJ_INVALID_ARGUMENTS);
      goto end;
    }

  owner_name = au_get_user_name (owner_mop);
  if (!owner_name)
    {
      ASSERT_ERROR_AND_SET (error);
      goto end;
    }
  sm_downcase_name (owner_name, downcase_owner_name, DB_MAX_USER_LENGTH);
  db_ws_free_and_init (owner_name);

  sprintf (serial_new_name, "%s.%s", downcase_owner_name, serial_name);

  serial_class_mop = sm_find_class (CT_SERIAL_NAME);
  if (serial_class_mop == NULL)
    {
      ERROR_SET_ERROR (error, ER_QPROC_DB_SERIAL_NOT_FOUND);
      goto end;
    }

  if (do_get_serial_obj_id (&serial_obj_id, serial_class_mop, serial_new_name) != NULL)
    {
      ERROR_SET_ERROR_1ARG (error, ER_QPROC_SERIAL_ALREADY_EXIST, serial_new_name);
      goto end;
    }

  obj_tmpl = dbt_edit_object (serial_mop);
  if (!obj_tmpl)
    {
      ASSERT_ERROR ();
      goto end;
    }

  /* unique_name */
  db_make_string (&value, serial_new_name);
  error = dbt_put_internal (obj_tmpl, SERIAL_ATTR_UNIQUE_NAME, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      is_abort = true;
      goto end;
    }

  /* owner */
  db_make_object (&value, owner_mop);
  error = dbt_put_internal (obj_tmpl, SERIAL_ATTR_OWNER, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      is_abort = true;
      goto end;
    }

  serial_obj = dbt_finish_object (obj_tmpl);
  if (!serial_obj)
    {
      ASSERT_ERROR_AND_SET (error);
      is_abort = true;
      goto end;
    }

end:
  if (is_abort && obj_tmpl)
    {
      dbt_abort_object (obj_tmpl);
    }

  AU_ENABLE (save);

  return error;
}

/*
 * au_change_trigger_owner - This changes the owning user of a trigger.
 *                           This should be called only by the DBA.
 *   return: error code
 *   trigger(in): trigger whose owner is to change
 *   owner(in):new owner
 */
int
au_change_trigger_owner (MOP trigger_mop, MOP owner_mop)
{
  DB_OBJECT *trigger_owner_obj = NULL;
  DB_OBJECT *target_class_obj = NULL;
  SM_CLASS *target_class = NULL;
  DB_VALUE value;
  const char *trigger_old_name = NULL;
  char trigger_new_name[DB_MAX_IDENTIFIER_LENGTH] = { '\0' };
  char *owner_name = NULL;
  char downcase_owner_name[DB_MAX_USER_LENGTH] = { '\0' };
  int save = 0;
  int error = NO_ERROR;

  if (!trigger_mop || !owner_mop)
    {
      ERROR_SET_WARNING (error, ER_OBJ_INVALID_ARGUMENTS);
      return error;
    }

  if (!au_is_dba_group_member (Au_user))
    {
      ERROR_SET_WARNING_1ARG (error, ER_AU_DBA_ONLY, "change_trigger_owner");
      return error;
    }

  AU_DISABLE (save);

  /*
   * class, serial, and trigger distinguish user schema by unique_name (user_specified_name).
   * so if the owner of class, serial, trigger changes, the unique_name must also change.
   */

  error = obj_get (trigger_mop, TR_ATT_UNIQUE_NAME, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }

  if (!DB_IS_STRING (&value) || (trigger_old_name = db_get_string (&value)) == NULL)
    {
      ERROR_SET_WARNING_1ARG (error, ER_TR_TRIGGER_NOT_FOUND, "");
      goto end;
    }

  /* Check if the owner to be changed is the same. */
  error = obj_get (trigger_mop, TR_ATT_OWNER, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }

  if (DB_VALUE_DOMAIN_TYPE (&value) != DB_TYPE_OBJECT || (trigger_owner_obj = db_get_object (&value)) == NULL)
    {
      /* Unable to get attribute value. */
      ERROR_SET_WARNING (error, ER_OBJ_INVALID_ARGUMENTS);
      goto end;
    }

  if (ws_is_same_object (trigger_owner_obj, owner_mop))
    {
      goto end;
    }

  /* TO BE: It is necessary to check the permission of the target class of the owner to be changed. */
#if 0
  error = obj_get (target_class_obj, TR_ATT_CLASS, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }

  error = au_fetch_class (target_class_obj, &target_class, AU_FETCH_READ, AU_SELECT);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }
#endif

  owner_name = au_get_user_name (owner_mop);
  if (!owner_name)
    {
      ASSERT_ERROR_AND_SET (error);
      goto end;
    }
  sm_downcase_name (owner_name, downcase_owner_name, DB_MAX_USER_LENGTH);
  db_ws_free_and_init (owner_name);

  snprintf (trigger_new_name, SM_MAX_IDENTIFIER_LENGTH, "%s.%s", downcase_owner_name,
	    sm_remove_qualifier_name (trigger_old_name));

  error = tr_rename_trigger (trigger_mop, trigger_new_name, false, true);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      goto end;
    }

  /* owner */
  db_make_object (&value, owner_mop);
  error = obj_set (trigger_mop, TR_ATT_OWNER, &value);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();

      /* abort on error */
      if (tr_rename_trigger (trigger_mop, trigger_old_name, false, true) != NO_ERROR)
	{
	  assert (false);
	}
    }

end:
  AU_ENABLE (save);

  return error;
}

/*
 * au_get_class_owner - This access the user object that is the owner of
 *                      the class.
 *   return: user object (owner of class_)
 *   classmop(in): class object
 */
MOP
au_get_class_owner (MOP classmop)
{
  MOP owner = NULL;
  SM_CLASS *class_;

  /* should we disable authorization here ? */
  /*
   * should we allow the class owner to be known if the active user doesn't
   * have select authorization ?
   */

  if (au_fetch_class_force (classmop, &class_, AU_FETCH_READ) == NO_ERROR)
    {
      owner = class_->owner;
      if (owner == NULL)
	{
	  /* shouln't we try to update the class if the owner wasn't set ? */
	  owner = Au_dba_user;
	}
    }

  return (owner);
}

int
au_change_class_owner (MOP class_mop, MOP owner_mop)
{
  int error = NO_ERROR;
  int i;
  MOP *sub_partitions = NULL;
  int is_partition = DB_NOT_PARTITIONED_CLASS;
  bool has_savepoint = false;
  MOP save_user, owner;
  const char *table_name;

  /* when changing the owner, all privileges are revoked */
  owner = au_get_class_owner (class_mop);
  if (owner == NULL)
    {
      ASSERT_ERROR_AND_SET (error);
      return error;
    }

  table_name = sm_get_ch_name (class_mop);
  if (table_name == NULL)
    {
      ASSERT_ERROR_AND_SET (error);
      return error;
    }

  save_user = Au_user;
  if (AU_SET_USER (owner) == NO_ERROR)
    {
      error = au_object_revoke_all_privileges (DB_OBJECT_CLASS, owner, table_name);
      if (error != NO_ERROR)
	{
	  AU_SET_USER (save_user);
	  return error;
	}
    }
  else
    {
      AU_SET_USER (save_user);
      return ER_FAILED;
    }
  AU_SET_USER (save_user);

  /* change the owner of a partition */
  error = sm_partitioned_class_type (class_mop, &is_partition, NULL, &sub_partitions);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
      return error;
    }

  if (is_partition == DB_PARTITION_CLASS)	/* if partition; error */
    {
      ERROR_SET_ERROR_1ARG (error, ER_NOT_ALLOWED_ACCESS_TO_PARTITION, "");
      goto end;
    }

  if (is_partition == DB_PARTITIONED_CLASS)	/* if partitioned class; do actions to all partitions */
    {
      error = tran_system_savepoint (UNIQUE_PARTITION_SAVEPOINT_OWNER);
      if (error != NO_ERROR)
	{
	  ASSERT_ERROR ();
	  goto end;
	}

      has_savepoint = true;

      for (i = 0; sub_partitions[i]; i++)
	{
	  error = au_change_class_owner_including_partitions (sub_partitions[i], owner_mop);
	  if (error != NO_ERROR)
	    {
	      ASSERT_ERROR ();
	      goto end;
	    }
	}
    }

  /* change the owner of a class */
  error = au_change_class_owner_including_partitions (class_mop, owner_mop);
  if (error != NO_ERROR)
    {
      ASSERT_ERROR ();
    }

end:
  if (sub_partitions)
    {
      free_and_init (sub_partitions);
    }

  if (has_savepoint && error != NO_ERROR && error != ER_LK_UNILATERALLY_ABORTED)
    {
      tran_abort_upto_system_savepoint (UNIQUE_PARTITION_SAVEPOINT_OWNER);
    }

  return error;
}

/*
 * au_change_sp_owner -
 *   return: error code
 *   parser(in):
 *   sp(in):
 *   owner(in):
 */
int
au_change_sp_owner (PARSER_CONTEXT *parser, MOP sp, MOP owner)
{
  int error = NO_ERROR;
  int save, lang;
  const char *name_str = NULL, *owner_str = NULL, *target_cls = NULL;
  char new_name_str[DB_MAX_IDENTIFIER_LENGTH];
  new_name_str[0]= '\0';
  char downcase_owner_name[DB_MAX_USER_LENGTH];
  downcase_owner_name[0] = '\0';
  DB_VALUE value, name_value, owner_value, sp_lang_val, target_cls_val;

  db_make_null (&value);
  db_make_null (&name_value);
  db_make_null (&owner_value);
  db_make_null (&sp_lang_val);
  db_make_null (&target_cls_val);

  assert (sp != NULL && owner != NULL);

  AU_DISABLE (save);

  /* change _db_stored_procedure */
  error = obj_get (sp, SP_ATTR_NAME, &name_value);
  if (error != NO_ERROR)
    {
      goto end;
    }
  error = obj_get (owner, "name", &owner_value);
  if (error != NO_ERROR)
    {
      goto end;
    }

  name_str = db_get_string (&name_value);
  owner_str = db_get_string (&owner_value);

  sm_downcase_name (owner_str, downcase_owner_name, DB_MAX_USER_LENGTH);
  sprintf (new_name_str, "%s.%s", downcase_owner_name, name_str);

  /* change the unique_name */
  db_make_string (&value, new_name_str);
  error = obj_set (sp, SP_ATTR_UNIQUE_NAME, &value);
  if (error < 0)
    {
      goto end;
    }

  /* change the owner */
  db_make_object (&value, owner);
  error = obj_set (sp, SP_ATTR_OWNER, &value);
  if (error < 0)
    {
      goto end;
    }

  /* check lang */
  error = db_get (sp, SP_ATTR_LANG, &sp_lang_val);
  if (error != NO_ERROR)
    {
      goto end;
    }

  lang = db_get_int (&sp_lang_val);
  if (lang == SP_LANG_PLCSQL)
    {
      error = db_get (sp, SP_ATTR_TARGET_CLASS, &target_cls_val);
      if (error != NO_ERROR)
	{
	  goto end;
	}
      target_cls = db_get_string (&target_cls_val);

      /* change _db_stored_procedure_code */
      error = alter_stored_procedure_code (parser, sp, target_cls, owner_str, 0);
      if (error != NO_ERROR)
	{
	  goto end;
	}
    }

end:

  AU_ENABLE (save);
  pr_clear_value (&value);
  pr_clear_value (&name_value);
  pr_clear_value (&owner_value);
  pr_clear_value (&sp_lang_val);
  pr_clear_value (&target_cls_val);

  return (error);
}

/*
 * au_change_sp_owner_with_privilege_cleanup -
 *   return: error code
 *   parser(in):
 *   sp_mop(in):
 *   owner_mop(in):
 */
int
au_change_sp_owner_with_privilege_cleanup (PARSER_CONTEXT *parser, MOP sp_mop, MOP new_owner_mop)
{
  int error = NO_ERROR;
  MOP owner_mop = NULL, save_user = NULL;
  char unique_name[DB_MAX_IDENTIFIER_LENGTH + 1];
  unique_name[0] = '\0';
  PARSER_CONTEXT *dummy_parser = NULL;

  assert (sp_mop != NULL && new_owner_mop != NULL);

  if (!au_is_dba_group_member (Au_user))
    {
      error = ER_AU_DBA_ONLY;
      er_set (ER_WARNING_SEVERITY, ARG_FILE_LINE, error, 1, "change_sp_owner");
      goto end;
    }

  /* create dummy parser of change_sp_owner_method */
  if (parser == NULL)
    {
      dummy_parser = parser_create_parser ();
      if (dummy_parser == NULL)
	{
	  error = ER_FAILED;
	  goto end;
	}

      parser = dummy_parser;
    }

  /* when changing the owner, all privileges are revoked */
  if (jsp_get_unique_name (sp_mop, unique_name, DB_MAX_IDENTIFIER_LENGTH) == NULL)
    {
      ASSERT_ERROR_AND_SET (error);
      goto end;
    }

  owner_mop = jsp_get_owner (sp_mop);
  if (owner_mop == NULL)
    {
      error = ER_FAILED;
      goto end;
    }

  save_user = Au_user;
  if (AU_SET_USER (owner_mop) == NO_ERROR)
    {
      error = au_object_revoke_all_privileges (DB_OBJECT_PROCEDURE, owner_mop, unique_name);
      if (error != NO_ERROR)
	{
	  AU_SET_USER (save_user);
	  goto end;
	}
    }
  else
    {
      AU_SET_USER (save_user);
      ASSERT_ERROR_AND_SET (error);
      goto end;
    }
  AU_SET_USER (save_user);

  /* change the owner of a sp */
  error = au_change_sp_owner (parser, sp_mop, new_owner_mop);

end:

  if (dummy_parser != NULL)
    {
      parser_free_parser (dummy_parser);
      parser = NULL;
    }

  return error;
}
