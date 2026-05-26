#  ============LICENSE_START===============================================
#  Copyright (C) 2026 OpenInfra Foundation Europe. All rights reserved.
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
"""Local Sphinx extension to generate standalone ReDoc HTML pages.

Usage in conf.py:

    import sys, os
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', 'docs', '_extensions'))
    extensions = ['redoc_gen', ...]

    redoc_pages = [
        {
            'page': 'my-api',
            'title': 'My API',
            'spec': os.path.join(os.path.dirname(__file__), '..', 'api', 'api.json'),
        },
    ]
"""

import json
import os

from jinja2 import Environment, FileSystemLoader


def generate_redoc_pages(app, exception):
    """Generate standalone ReDoc HTML pages from OpenAPI specs."""
    if exception:
        return

    redoc_pages = getattr(app.config, 'redoc_pages', [])
    if not redoc_pages:
        return

    template_dir = os.path.join(os.path.dirname(__file__), '..', '_templates')
    env = Environment(loader=FileSystemLoader(template_dir))
    template = env.get_template('redoc.html')

    for page in redoc_pages:
        with open(page['spec'], 'r') as f:
            spec_contents = json.dumps(json.load(f))

        html = template.render(
            title=page['title'],
            project=app.config.project,
            spec=spec_contents,
            pathto=lambda x, y=0: '_static/' + x.split('_static/')[-1] if '_static' in x else x,
        )

        outfile = os.path.join(app.builder.outdir, page['page'] + '.html')
        with open(outfile, 'w') as f:
            f.write(html)


def setup(app):
    app.add_config_value('redoc_pages', [], 'html')
    app.connect('build-finished', generate_redoc_pages)
    return {'version': '0.1', 'parallel_read_safe': True}
