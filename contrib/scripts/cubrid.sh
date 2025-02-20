#
#
#  Copyright 2016 CUBRID Corporation
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

export CUBRID=/opt/cubrid
export CUBRID_DATABASES=$CUBRID/databases

LD_LIBRARY_PATH=$CUBRID/lib:$CUBRID/cci/lib:$LD_LIBRARY_PATH
SHLIB_PATH=$LD_LIBRARY_PATH
LIBPATH=$LD_LIBRARY_PATH
PATH=$CUBRID/bin:/usr/sbin:$PATH
export LD_LIBRARY_PATH SHLIB_PATH LIBPATH PATH

#
#  tuning setting for glib memory library
#
#export MALLOC_MMAP_MAX_=65536            # default : 65536
#export MALLOC_MMAP_THRESHOLD_=131072     # default : 131072 (128K)
export MALLOC_TRIM_THRESHOLD_=0          # default : 131072 (128K)
#export MALLOC_ARENA_MAX=                 # default : core * 8

#
# preloading library for another memory library
#
#export LD_PRELOAD=/usr/lib64/jemalloc.so.1
