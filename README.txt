An attempt to be as minimal as possible for a server-side
repeater for websockets.

The Jetty implemenatation acts as a websockets server side endpoint.
Clients that send a JSON message to it will have that message repeated
out to all other clients.

BSD License. Copyright 2014 MOVES Institute.
