# Http Request Plugin for Jenkins

This plugin sends a HTTP/HTTPS request to a speficied URL, with the build parameters optionally
appended to the URL as key value pairs.

## Features:

* Select the HTTP method: GET, POST, PUT, DELETE, or HEAD
* Select a range of expected response codes (a response code outside the range fails the build)
* Specify a username / password in the global configuration, that can be used for Basic Digest Authentication
* Supports From Authentication
* Specify a string that must be present in the response, if the string is not present, the build fails
* Store the response to a file
* Set a connection timeout limit (build fails if timeout is exceeded)
* Send an "Accept" header
* Send a "Content-type" header

## When using this plugin as a Pipeline step:

* The build parameters configuration is ignored, you must append build parameters programmatically to the url. This is more flexible as you can cherry pick which build parameters to pass.

### Pipeline example:

In a Pipeline job, you have total control over how the url is
formed. Suppose you have a build parameter called "pretty",
you can pass it to the HTTP request programmatically with:

    node () {
        step([$class:
            'HttpRequest',
            url: "http://httpbin.org/response-headers?pretty=${pretty}",
            consoleLogResponseBody: true
        ])
    }

