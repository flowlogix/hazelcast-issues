# hazelcast-issues
Tests Hz issues

## Cache Tester
By default, it connects to clients on ports 5710-5730

To run from Maven: `mvn -Prun test` (default port, 5710)

```
$ mvn -Prun test -Dhz.port=5710
$ mvn -Prun,debug test -Dhz.port=5710
$ mvn -Prun test -Dhz.port=5710 -Dhz.raft=false
```
See https://github.com/flowlogix/hazelcast-issues/blob/1a8d90d71423387423c1300f4f92a4bc30c5e2e3/CacheTester/pom.xml#L12 for all available propties
