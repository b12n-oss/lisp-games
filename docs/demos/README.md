# Demos

One animated preview per port: two games across four runtimes. Every GIF here is committed, so nothing in this repo needs a capture toolchain to build. They were recorded with `screen-grab` over `cgevent`, internal b12n tools that are not public yet; `bb record` regenerates them on a machine that has both.

## helitorus

### helitorus-babashka

raylib over the babashka.ffi built into bb. Scalars throughout by choice, and the surface emitted as one rlgl batch per ring.

![helitorus-babashka](helitorus-babashka.gif)

### helitorus-jolt

raylib over net.b12n/raylib on jolt.ffi. Same geometry, typed primitive arrays, about three and a half times the arithmetic throughput.

![helitorus-jolt](helitorus-jolt.gif)

### helitorus-clojure

raylib over raylib-clj, which binds it with coffi on Panama. Structs arrive as maps; rlgl is declared in the example itself. Fastest of the four, and the array type hints are what make it so.

![helitorus-clojure](helitorus-clojure.gif)

### helitorus-jank

raylib over jank's cpp/ interop, including raylib.h directly. No primitive arrays in jank, so the buffers are malloc'd natives carried as boxes.

![helitorus-jank](helitorus-jank.gif)

## mesh-instancing

### mesh-instancing-babashka

raylib's own instancing example. Mesh (120 bytes) and Material (40) both cross by value in one call, alongside a matrix array in foreign memory.

![mesh-instancing-babashka](mesh-instancing-babashka.gif)

### mesh-instancing-jolt

the same, over the raylib-jlt wrapper, which already carries the struct-by-value work.

![mesh-instancing-jolt](mesh-instancing-jolt.gif)

### mesh-instancing-clojure

the same, declaring seven calls and its own Shader/Material aliases, because raylib-clj's split the pointer and flatten the nesting.

![mesh-instancing-clojure](mesh-instancing-clojure.gif)

### mesh-instancing-jank

the same, and the only port where Mesh and Material cross by value with no ceremony, because jank has the real header.

![mesh-instancing-jank](mesh-instancing-jank.gif)

