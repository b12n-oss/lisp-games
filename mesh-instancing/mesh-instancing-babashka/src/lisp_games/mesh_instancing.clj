;; Mesh instancing, after raylib's examples/shaders/shaders_mesh_instancing.c
;; by Ramon Santamaria (@raysan5) and contributors, zlib licensed. The lighting
;; and fog shaders follow the jolt port in jlt-commons/raylib-jlt.
;;
;; See NOTICE.md at the root of this repository for the notices in full.

(ns lisp-games.mesh-instancing
  "Ten thousand lit cubes in a single draw call.

  Run it with `bb mesh`, or `bb mesh 10` to quit after ten seconds.

  Instancing moves the per-copy transform out of the draw loop and into a
  vertex attribute. One mesh and one material go to the GPU alongside an array
  of N matrices, and the vertex stage reads its own matrix per instance from
  `in mat4 instanceTransform`. The alternative, a DrawMesh per cube, is N draw
  calls a frame.

  Nothing has to be wired up for that attribute. raylib 6.0 resolves it by name
  when the shader loads.

  Two modes, because the faithful example is a GPU showcase rather than a
  benchmark:

    static  the matrices are built once and the camera orbits. This is what
            raylib does. Per frame the CPU does almost nothing, so every
            runtime reports the same frame rate and the number tells you about
            your GPU.
    spin    every matrix is rebuilt every frame. Now the per-instance
            arithmetic and the marshalling into foreign memory are the frame,
            which is what actually separates one runtime from another.

  What is specific to this port: babashka.ffi carries structs by value, and
  this example needs it badly. A raylib Mesh is 120 bytes returned by value
  from GenMeshCube, a Material is 40 and comes back from LoadMaterialDefault,
  and DrawMeshInstanced takes both of them by value plus a pointer to the
  matrix array. All of it is declared below as ordinary layouts."
  (:require [babashka.ffi :as ffi :refer [defcfn]]))

(ffi/load-system-library "raylib")

;; --- the raylib types this needs ---------------------------------------------
;; Every size here was checked against a C program compiled with the same
;; header: Mesh 120, Material 40, Matrix 64, Camera3D 44, MaterialMap 28.

(def Vector3 [:struct [[:x :float] [:y :float] [:z :float]]])
(def Color   [:struct [[:r :uint8] [:g :uint8] [:b :uint8] [:a :uint8]]])
(def Shader  [:struct [[:id :uint] [:locs :pointer]]])
(def Texture [:struct [[:id :uint] [:width :int] [:height :int]
                       [:mipmaps :int] [:format :int]]])
(def MaterialMap [:struct [[:texture Texture] [:color Color] [:value :float]]])
(def Material [:struct [[:shader Shader] [:maps :pointer]
                        [:params [:array :float 4]]]])
(def Camera3D [:struct [[:position Vector3] [:target Vector3] [:up Vector3]
                        [:fovy :float] [:projection :int]]])

;; raylib declares Matrix as m0,m4,m8,m12 / m1,m5,m9,m13 / ... so in
;; DECLARATION order the translation column lands at slots 3, 7 and 11.
(def Matrix [:struct (mapv (fn [i] [(keyword (str "f" i)) :float]) (range 16))])

(def Mesh
  [:struct [[:vertexCount :int] [:triangleCount :int]
            [:vertices :pointer] [:texcoords :pointer] [:texcoords2 :pointer]
            [:normals :pointer] [:tangents :pointer] [:colors :pointer]
            [:indices :pointer] [:boneCount :int] [:boneIndices :pointer]
            [:boneWeights :pointer] [:animVertices :pointer]
            [:animNormals :pointer] [:vaoId :uint] [:vboId :pointer]]])

;; --- the raylib surface ------------------------------------------------------

(defcfn init-window "InitWindow" [:int :int :string] :void)
(defcfn close-window "CloseWindow" [] :void)
(defcfn window-should-close "WindowShouldClose" [] :uint8)
(defcfn set-target-fps "SetTargetFPS" [:int] :void)
(defcfn set-config-flags "SetConfigFlags" [:uint] :void)
(defcfn begin-drawing "BeginDrawing" [] :void)
(defcfn end-drawing "EndDrawing" [] :void)
(defcfn clear-background "ClearBackground" [Color] :void)
(defcfn draw-text "DrawText" [:string :int :int :int Color] :void)
(defcfn get-frame-time "GetFrameTime" [] :float)
(defcfn take-screenshot "TakeScreenshot" [:string] :void)
(defcfn rl-draw-render-batch-active "rlDrawRenderBatchActive" [] :void)
(defcfn begin-mode-3d "BeginMode3D" [Camera3D] :void)
(defcfn end-mode-3d "EndMode3D" [] :void)
;; the three that need struct-by-value in both directions
(defcfn gen-mesh-cube "GenMeshCube" [:float :float :float] Mesh)
(defcfn load-material-default "LoadMaterialDefault" [] Material)
(defcfn draw-mesh-instanced "DrawMeshInstanced" [Mesh Material :pointer :int] :void)
(defcfn load-shader-from-memory "LoadShaderFromMemory" [:string :string] Shader)
(defcfn get-shader-location "GetShaderLocation" [Shader :string] :int)
(defcfn set-shader-value "SetShaderValue" [Shader :int :pointer :int] :void)
(defcfn unload-shader "UnloadShader" [Shader] :void)
(defcfn unload-mesh "UnloadMesh" [Mesh] :void)

(def ^:const SHADER-UNIFORM-FLOAT 0)
(def ^:const SHADER-UNIFORM-VEC3 2)
(def ^:const SHADER-UNIFORM-VEC4 3)
(def ^:const SHADER-UNIFORM-INT 4)
(def ^:const FLAG-MSAA-4X-HINT 0x20)
(def ^:const MATERIAL-MAP-DIFFUSE 0)

(def W 800)
(def H 450)
(def SPREAD 52.0)
(def TAU 6.283185307179586)

(def vertex-shader "
#version 330
in vec3 vertexPosition;
in vec2 vertexTexCoord;
in vec3 vertexNormal;
in mat4 instanceTransform;
uniform mat4 mvp;
uniform mat4 matNormal;
out vec3 fragPosition;
out vec2 fragTexCoord;
out vec3 fragNormal;
void main()
{
    fragPosition = vec3(instanceTransform*vec4(vertexPosition, 1.0));
    fragTexCoord = vertexTexCoord;
    fragNormal = normalize(vec3(matNormal*vec4(vertexNormal, 1.0)));
    gl_Position = mvp*instanceTransform*vec4(vertexPosition, 1.0);
}
")

(def fragment-shader "
#version 330
in vec3 fragPosition;
in vec2 fragTexCoord;
in vec3 fragNormal;
uniform vec4 colDiffuse;
out vec4 finalColor;

#define MAX_LIGHTS 4

struct Light {
    int enabled;
    int type;
    vec3 position;
    vec3 target;
    vec4 color;
};

uniform Light lights[MAX_LIGHTS];
uniform vec4 ambient;
uniform vec3 viewPos;
uniform vec4 fogColor;
uniform float fogDensity;

void main()
{
    vec3 lightDot = vec3(0.0);
    vec3 normal = normalize(fragNormal);
    vec3 viewD = normalize(viewPos - fragPosition);
    vec3 specular = vec3(0.0);

    for (int i = 0; i < MAX_LIGHTS; i++)
    {
        if (lights[i].enabled == 1)
        {
            vec3 light = vec3(0.0);
            if (lights[i].type == 0) light = -normalize(lights[i].target - lights[i].position);
            else light = normalize(lights[i].position - fragPosition);

            float NdotL = max(dot(normal, light), 0.0);
            lightDot += lights[i].color.rgb*NdotL;

            float specCo = 0.0;
            if (NdotL > 0.0) specCo = pow(max(0.0, dot(viewD, reflect(-light, normal))), 16.0);
            specular += specCo;
        }
    }

    finalColor = (colDiffuse + vec4(specular, 1.0))*vec4(lightDot, 1.0);
    finalColor += (ambient/10.0);
    finalColor = pow(finalColor, vec4(1.0/2.2));

    float dist = length(viewPos - fragPosition);
    float fogFactor = clamp(1.0/exp((dist*fogDensity)*(dist*fogDensity)), 0.0, 1.0);
    finalColor = mix(fogColor, finalColor, fogFactor);
}
")

;; --- the per-instance matrix -------------------------------------------------

(defn rot-translate
  "An axis-angle rotation with a translation, in raylib's Matrix field order.
  This is the arithmetic `spin` mode repeats for every instance every frame."
  [ax ay az angle tx ty tz]
  (let [c (Math/cos angle)
        s (Math/sin angle)
        t (- 1.0 c)]
    {:f0 (+ (* t ax ax) c)        :f1 (- (* t ax ay) (* s az))
     :f2 (+ (* t ax az) (* s ay)) :f3 tx
     :f4 (+ (* t ax ay) (* s az)) :f5 (+ (* t ay ay) c)
     :f6 (- (* t ay az) (* s ax)) :f7 ty
     :f8 (- (* t ax az) (* s ay)) :f9 (+ (* t ay az) (* s ax))
     :f10 (+ (* t az az) c)       :f11 tz
     :f12 0.0 :f13 0.0 :f14 0.0 :f15 1.0}))

(defn scatter
  "One random placement per instance: a position in a cube of side SPREAD, a
  unit-ish axis and a starting angle. Generated once, whatever the mode."
  [n]
  (vec (for [_ (range n)]
         (let [ax (+ 0.1 (rand 1.0))
               ay (+ 0.1 (rand 1.0))
               az (+ 0.1 (rand 1.0))
               len (Math/sqrt (+ (* ax ax) (* ay ay) (* az az)))]
           [(/ ax len) (/ ay len) (/ az len)
            (rand TAU)
            (- (rand SPREAD) (/ SPREAD 2.0))
            (- (rand SPREAD) (/ SPREAD 2.0))
            (- (rand SPREAD) (/ SPREAD 2.0))]))))

(defn transforms-for
  "Every instance's matrix, at time `t`. In static mode t is always 0."
  [places t]
  (mapv (fn [[ax ay az a0 tx ty tz]]
          (rot-translate ax ay az (+ a0 t) tx ty tz))
        places))

;; --- uniforms ----------------------------------------------------------------

(defn set-vec! [arena sh name kind vals]
  (let [p (ffi/alloc arena [:array :float (count vals)])]
    (ffi/write p [:array :float (count vals)] (mapv float vals))
    (set-shader-value sh (get-shader-location sh name) p kind)))

(defn set-int! [arena sh name v]
  (let [p (ffi/alloc arena :int)]
    (ffi/write p :int v)
    (set-shader-value sh (get-shader-location sh name) p SHADER-UNIFORM-INT)))

(defn light! [arena sh i kind [px py pz] [r g b]]
  (set-int! arena sh (str "lights[" i "].enabled") 1)
  (set-int! arena sh (str "lights[" i "].type") kind)
  (set-vec! arena sh (str "lights[" i "].position") SHADER-UNIFORM-VEC3 [px py pz])
  (set-vec! arena sh (str "lights[" i "].target") SHADER-UNIFORM-VEC3 [0.0 0.0 0.0])
  (set-vec! arena sh (str "lights[" i "].color") SHADER-UNIFORM-VEC4
            [(/ r 255.0) (/ g 255.0) (/ b 255.0) 1.0]))

;; --- the loop ----------------------------------------------------------------

(defn -main
  "Open a window and run until closed.

  bb mesh                          run until the window is closed
  bb mesh 10                       quit after ten seconds of frame time
  bb mesh 5 out.png 40             quit after five, capture frame 40
  bb mesh 5 out.png 40 2000        the same, with 2000 instances
  bb mesh 5 out.png 40 2000 spin   rebuild every matrix every frame"
  [& args]
  (let [[secs shot-path shot-frame n mode] args
        deadline (some-> secs parse-long)
        shot-frame (or (some-> shot-frame parse-long) 40)
        n (or (some-> n parse-long) 10000)
        spin? (= "spin" mode)]
    (set-config-flags FLAG-MSAA-4X-HINT)
    (init-window W H "lisp-games - mesh instancing")
    (set-target-fps 120)
    (with-open [arena (ffi/confined-arena)]
      (let [sh (load-shader-from-memory vertex-shader fragment-shader)
            mat (-> (load-material-default) (assoc :shader sh))
            cube (gen-mesh-cube 0.85 0.85 0.85)
            arr (ffi/alloc arena [:array Matrix n])
            arr-layout [:array Matrix n]
            places (scatter n)
            view-pos (get-shader-location sh "viewPos")
            vp (ffi/alloc arena [:array :float 3])]
        (set-vec! arena sh "ambient" SHADER-UNIFORM-VEC4 [0.35 0.35 0.4 1.0])
        (set-vec! arena sh "fogColor" SHADER-UNIFORM-VEC4 [0.09 0.10 0.14 1.0])
        (set-vec! arena sh "fogDensity" SHADER-UNIFORM-FLOAT [0.028])
        (light! arena sh 0 1 [22.0 26.0 22.0] [255 244 220])
        (light! arena sh 1 1 [-24.0 -14.0 -18.0] [90 150 255])
        (set-int! arena sh "lights[2].enabled" 0)
        (set-int! arena sh "lights[3].enabled" 0)
        ;; static mode writes the array once, here
        (ffi/write arr arr-layout (transforms-for places 0.0))
        (loop [frame 0 t 0.0 frames 0 acc 0.0 b-acc 0.0 d-acc 0.0
               hud "measuring..."]
          (when (and (zero? (window-should-close))
                     (or (nil? deadline) (< t deadline)))
            (let [dt (min 0.05 (get-frame-time))
                  t2 (+ t dt)
                  ang (* t2 0.5)
                  cam-x (* 46.0 (Math/sin (* t2 0.25)))
                  cam-z (* 46.0 (Math/cos (* t2 0.25)))
                  b0 (System/nanoTime)
                  _ (when spin? (ffi/write arr arr-layout (transforms-for places ang)))
                  b1 (System/nanoTime)]
              (ffi/write vp [:array :float 3] [(float cam-x) 12.0 (float cam-z)])
              (set-shader-value sh view-pos vp SHADER-UNIFORM-VEC3)
              (begin-drawing)
              (clear-background {:r 23 :g 26 :b 36 :a 255})
              (begin-mode-3d {:position {:x cam-x :y 12.0 :z cam-z}
                              :target {:x 0.0 :y 0.0 :z 0.0}
                              :up {:x 0.0 :y 1.0 :z 0.0}
                              :fovy 45.0 :projection 0})
              (let [d0 (System/nanoTime)
                    _ (draw-mesh-instanced cube mat arr n)
                    d1 (System/nanoTime)]
                (end-mode-3d)
                (let [frames2 (inc frames)
                      acc2 (+ acc dt)
                      b-acc2 (+ b-acc (/ (- b1 b0) 1e6))
                      d-acc2 (+ d-acc (/ (- d1 d0) 1e6))
                      refresh? (>= acc2 0.4)
                      hud2 (if refresh?
                             (format "fps %.0f | build %.1f ms | draw %.1f ms | %d cubes | %s"
                                     (/ frames2 acc2) (/ b-acc2 frames2)
                                     (/ d-acc2 frames2) n (if spin? "spin" "static"))
                             hud)]
                  (when refresh? (println hud2))
                  (draw-text "raylib [shaders] example - mesh instancing" 16 14 20
                             {:r 245 :g 245 :b 245 :a 255})
                  (draw-text (str n " lit cubes in one draw call") 16 40 14
                             {:r 165 :g 180 :b 210 :a 255})
                  (draw-text hud2 16 (- H 30) 16 {:r 165 :g 180 :b 210 :a 255})
                  (when (and shot-path (= frame shot-frame))
                    (rl-draw-render-batch-active)
                    (take-screenshot shot-path))
                  (end-drawing)
                  (recur (inc frame) t2
                         (if refresh? 0 frames2) (if refresh? 0.0 acc2)
                         (if refresh? 0.0 b-acc2) (if refresh? 0.0 d-acc2)
                         hud2))))))
        (unload-mesh cube)
        (unload-shader sh)))
    (close-window)))
