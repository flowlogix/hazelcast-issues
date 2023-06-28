# hazelcast-issues
Tests Hz issues

## Cache Tester
By default, it connects to members on ports 5710-5730

To run from Maven: `mvn -Prun test` (default port, 5710)

```
$ mvn -Prun test
$ mvn -Prun,debug
$ mvn -Prun,debug test -Dhz.port=5710 -Dhz.raft=false
```
See https://github.com/flowlogix/hazelcast-issues/blob/master/CacheTester/pom.xml#L12 for all available propties
