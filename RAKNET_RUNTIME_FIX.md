# RakNet runtime fix

The previous build compiled because `dev.kastle.netty:netty-transport-raknet:1.7.4`
was present on the compile/runtime configuration, but the dependency was not bundled
into the Fabric mod JAR. As a result, launching a Bedrock connection failed with
`NoClassDefFoundError`/`ClassNotFoundException` for
`org.cloudburstmc.netty.channel.raknet.RakChannelFactory`.

The build now uses both `implementation` and Fabric Loom `include` for the same
RakNet transport dependency, with `transitive = false` so Minecraft's own Netty
is reused rather than embedding another Netty copy.
