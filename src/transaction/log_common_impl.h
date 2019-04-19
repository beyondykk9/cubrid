/*
 * Copyright (C) 2008 Search Solution Corporation. All rights reserved by Search Solution.
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

/*
 * log implementation common part to server/client modules.
 */

#ifndef _LOG_COMMON_IMPL_H_
#define _LOG_COMMON_IMPL_H_

#include "file_io.h"
#include "log_comm.h"
#include "log_lsa.hpp"
#include "log_record.hpp"
#include "recovery.h"
#include "release_string.h"
#include "storage_common.h"
#include "system.h"

/************************************************************************/
/* Section shared with client... TODO: remove any code accessing log    */
/* module on client. Most are used by log_writer.c and log_applier.c    */
/************************************************************************/

/* Uses 0xff to fills up the page, before writing in it. This helps recovery to detect the end of the log in
 * case of log page corruption, caused by partial page flush. Thus, at recovery analysis, we can easily
 * detect the last valid log record - the log record having NULL_LSA (0xff) in its forward address field.
 * If we do not use 0xff, a corrupted log record will be considered valid at recovery, thus affecting
 * the database consistency.
 */
#define LOG_PAGE_INIT_VALUE 0xff

#define NUM_NORMAL_TRANS (prm_get_integer_value (PRM_ID_CSS_MAX_CLIENTS))
#define NUM_SYSTEM_TRANS 1
#define NUM_NON_SYSTEM_TRANS (css_get_max_conn ())
#define MAX_NTRANS \
  (NUM_NON_SYSTEM_TRANS + NUM_SYSTEM_TRANS)

#define VACUUM_NULL_LOG_BLOCKID -1

enum LOG_HA_FILESTAT
{
  LOG_HA_FILESTAT_CLEAR = 0,
  LOG_HA_FILESTAT_ARCHIVED = 1,
  LOG_HA_FILESTAT_SYNCHRONIZED = 2
};

#if !defined (NDEBUG) && !defined (WINDOWS)
extern int logtb_collect_local_clients (int **local_client_pids);
#endif /* !defined (NDEBUG) && !defined (WINDOWS) */

/************************************************************************/
/* End of part shared with client.                                      */
/************************************************************************/

#endif // _LOG_COMMON_IMPL_H_
