// -
//
//	========================LICENSE_START=================================
//	O-RAN-SC
//	%%
//	Copyright (C) 2023: Nordix Foundation
//	%%
//	Licensed under the Apache License, Version 2.0 (the "License");
//	you may not use this file except in compliance with the License.
//	You may obtain a copy of the License at
//
//	     http://www.apache.org/licenses/LICENSE-2.0
//
//	Unless required by applicable law or agreed to in writing, software
//	distributed under the License is distributed on an "AS IS" BASIS,
//	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//	See the License for the specific language governing permissions and
//	limitations under the License.
//	========================LICENSE_END===================================
package utils

import (
	"bytes"
	log "github.com/sirupsen/logrus"
	"main/components/kafkacollector"
	"net/http"
)

var httpclient = &http.Client{}

// Send a http request with json (json may be nil)
func Send_http_request(json []byte, method string, url string, retry bool, useAuth bool) bool {

	// set the HTTP method, url, and request body
	var req *http.Request
	var err error
	if json == nil {
		req, err = http.NewRequest(method, url, http.NoBody)
	} else {
		req, err = http.NewRequest(method, url, bytes.NewBuffer(json))
		req.Header.Set("Content-Type", "application/json; charset=utf-8")
	}
	if err != nil {
		log.Error("Cannot create http request, method: ", method, " url: ", url)
		return false
	}

	if useAuth {
		token, err := kafkacollector.Fetch_token()
		if err != nil {
			log.Error("Cannot fetch token for http request: ", err)
			return false
		}
		req.Header.Set("Authorization", "Bearer "+token.TokenValue)
	}

	log.Debug("HTTP request: ", req)

	log.Debug("Sending http request")
	resp, err2 := httpclient.Do(req)
	if err2 != nil {
		log.Error("Http request error: ", err2)
		log.Error("Cannot send http request method: ", method, " url: ", url)
	} else {
		if resp.StatusCode == 200 || resp.StatusCode == 201 || resp.StatusCode == 204 {
			log.Debug("Accepted http status: ", resp.StatusCode)
			resp.Body.Close()
			return true
		}
		log.Debug("HTTP resp: ", resp)
		resp.Body.Close()
	}
	return false
}
