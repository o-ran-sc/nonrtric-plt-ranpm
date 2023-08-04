# O-RAN-SC Non-RealTime RIC PM Producer

This product is a PM data information producer (as defined by the Information Coordinator Service (ICS)). Its main tasks is to distribute PM data to PM data consumers (using Kafka topics).

This service will receive File Ready Events or PM Data from the Kafka streaming platform and will filter the information and distribute it further to the data consumers (information job owners).

The component is a springboot service and is configured as any springboot service through the file `config/application.yaml`. The component log can be retrieved and logging can be controled by means of REST call. See the API documentation (api/api.yaml).

The file `config/application_configuration.json` contains the configuration of job types that the producer will support.



```sh
{
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {

     TBD

  },
  "additionalProperties": false
}
```

The latest documentation can be found here:
https://docs.o-ran-sc.org/projects/o-ran-sc-nonrtric-plt-ranpm/en/latest/pmproducer/index.html


The comonent is part of the RAM PM use case, which is described here:
https://docs.o-ran-sc.org/projects/o-ran-sc-nonrtric-plt-ranpm/en/latest/overview.html#overview


## License

Copyright (C) 2023 Nordix Foundation. Licensed under the Apache License, Version 2.0 (the "License") you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
For more information about license please see the [LICENSE](LICENSE.txt) file for details.
