package utils

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestSend_http_request(t *testing.T) {
	type testCase struct {
		Name string

		Json    []byte
		Method  string
		Url     string
		Retry   bool
		UseAuth bool

		ExpectedBool bool
	}

	validate := func(t *testing.T, tc *testCase) {
		t.Run(tc.Name, func(t *testing.T) {
			actualBool := SendHttpRequest(tc.Json, tc.Method, tc.Url, tc.Retry, tc.UseAuth)

			assert.Equal(t, tc.ExpectedBool, actualBool)
		})
	}

	validate(t, &testCase{})
}
