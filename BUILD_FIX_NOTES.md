# Build fix 4

The previous build removed CubeConverter, but ViaBedrock source still imports `org.cube.converter.*` in the resource-pack/entity pipeline. That caused `BedrockAttachableData` and related symbols to fail compilation.

This revision restores the exact upstream ViaBedrock CubeConverter dependency (`com.github.oryxel1:CubeConverter:abecffaaea`) and the JitPack repository, using `implementation` plus Loom `include` with `transitive = false`.

This is a dependency restoration only; it does not change the inventory bridge logic.
