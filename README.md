# co2

## Build and run
Build requirements:
* Java JDK
* [Leiningen](https://leiningen.org/) 

To run either `lein run`
or `lein uberjar && java -jar target/co2-0.1.0-SNAPSHOT-standalone.jar`.  

The server listens on 127.0.0.1:8080.

## Implemented parts
* Receiving measurements. 
* Getting sensor status.


## Implementation notes

It is assumed that set of sensors is not fixed. So if there's 
no data for a sensor we assume that it's data has not arrived yet.
Therefore a status UNKNOWN is also added. I decided not to return 404
as it is not a client fault - one may not know in advance if data  
should be there unless we specify list of sensors.
Also if there are no messages for a sensor for a long time 
it probably should be set to UNKNOWN state. 

If server stops for any reason, we may fail to detect some
conditions (ones that consider several consecutive events) 
until memory cache warms up again. It is possible to avoid that 
(e.g. by storing recent events in Kafka) but it will complicate
implementation.

Incoming data estimation:
1M sensors, sending 1 message per minute amounts to
~17K events per second - may spike especially if server is
 temporarily unavailable and sensors retry.
For 30 days minimal data volume: 
(8 bytes timestamp + 2 bytes measurement) * 60 minutes * 24 hours * 30 days 
= 432 Kb per sensor, or 432Gb for 1M sensors.

Max and Averages - absolutely precise values would require to keep all 
the measurements. One may optimize that by aggregation - keeping 
one per-day value. 

Only status changes are recorded (e.g. repeating consecutive 
OKs or ALERTs are not stored, only first one).

API path misprint "mesurements" implemented as "measurements".

API for listing alerts: it suggests that there are 3 measurements, 
but what if alert lasts for, say hour - are those 3 latest measurements? 
How are they selected? Or we should produce alert every minute 
(with every new incoming message)?
In real application I'd have an option to query actual measurements 
for interesting period of time.

Format of sensor assumed to be UUID (GUID).

No performance optimization were attempted, I tried to make it work first.
The server can be scaled by setting up several instances and spreading
sensors evenly among them. For higher loads the code 
can be optimized by using mutable data structures (e.g. ones from `java.util. ...`).

It is not specified if a sensor can send measurements out of 
order or send duplicates (multiple measurements with same timestamp).
To keep initial implementation simple assuming all measurements
for a sensor are posted in order.

It is not specified how frequent queries would be - for decent performance 
we'll need an external storage server (PostgreSQL or Cassandra). 
Since sensors to not depend on each other a key-value storage may suffice.
But for more complex queries one would want something like PostgreSQL.
This part has only a stub implementation - one can send events 
(status changes) to a function with, e.g.
```
 (in-ns `co2.core)
 (flush-statuses! (:events state) println).  
```

I have never worked with Macs but hopefully Bash and OpenJDK 
are available. The build requires Leiningen - which is a Bash script.

## Resources

* [Http-Kit](http://www.http-kit.org/server.html)
* [Composure](https://github.com/weavejester/compojure)
* [Leiningen](https://leiningen.org/)
* [Clojure](https://clojure.org/)
