#  ============LICENSE_START===============================================
#  Copyright (C) 2021-2023 Nordix Foundation. All rights reserved.
#  ========================================================================
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#  ============LICENSE_END=================================================
#

# NB: This is the only conf.yaml file used by the upstream readthedocs job  (ref '.readthedocs.yaml')
#     Where possible include contents of the sub-folders' 'conf.yaml' files here if appropriate

from docs_conf.conf import *

branch = 'latest'
baseurl = 'https://docs.o-ran-sc.org/projects/'
selfurl = '%s/o-ran-sc-nonrtric-plt-ranpm/en/%s' %(baseurl, branch)

linkcheck_ignore = [
    'http://localhost.*',
    'http://127.0.0.1.*',
    'https://gerrit.o-ran-sc.org.*'
]

extensions = [
    'sphinx.ext.intersphinx',
    'sphinx.ext.autosectionlabel',
]

#intershpinx mapping with other projects
intersphinx_mapping = {}
intersphinx_mapping['nonrtric'] = ('%s/o-ran-sc-nonrtric/en/%s' %(baseurl, branch), None)
## Note there is a circular dependency here - sub-project pages must exist before they can be checked
intersphinx_mapping['influxlogger'] = ('%s/influxlogger' % selfurl, None)
intersphinx_mapping['datafilecollector'] = ('%s/datafilecollector' % selfurl, None)
intersphinx_mapping['pmproducer'] = ('%s/pmproducer' % selfurl, None)
intersphinx_mapping['pm-file-converter'] = ('%s/pm-file-converter' % selfurl, None)

intersphinx_disabled_reftypes = ["*"]
