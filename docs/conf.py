from docs_conf import *

branch = 'latest'
selfurl = 'https://docs.o-ran-sc.org/projects/o-ran-sc-nonrtric-plt-ranpm/en/%s' % branch

linkcheck_ignore = [
    'http://localhost.*',
    'http://127.0.0.1.*',
    'https://gerrit.o-ran-sc.org.*'
]

#branch configuration


linkcheck_ignore = [
    'http://localhost.*',
    'http://127.0.0.1.*',
    'https://gerrit.o-ran-sc.org.*',
]

extensions = [
    'sphinx.ext.intersphinx',
    'sphinx.ext.autosectionlabel',
]

#intershpinx mapping with other projects
#intersphinx_mapping = {}
## Note there is a circular dependency here - sub-project pages must exist before they can be checked 
#intersphinx_mapping['influxlogger'] = ('%s/influxlogger' % selfurl, None)
#intersphinx_mapping['datafilecollector'] = ('%s/datafilecollector' % selfurl, None)
#intersphinx_mapping['pmproducer'] = ('%s/pmproducer' % selfurl, None)


intersphinx_disabled_reftypes = ["*"]
