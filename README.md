This plugin does a HTTP/HTTPS request to a speficied URL with the build parameters automatically
part of the payload as key/value pairs.

It can be configured whether GET or POST (default) should be used as HTTP method, as well
as if the status return code of the response marks the build as failed (default) or is ignored.
You can specify different username / password pairs in the global configuration to support
authenticated HTTP/HTTPS requests, which you then refer to from your build configuration.

