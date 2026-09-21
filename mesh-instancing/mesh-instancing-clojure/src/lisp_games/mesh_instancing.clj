;; Mesh instancing, after raylib's examples/shaders/shaders_mesh_instancing.c
;; by Ramon Santamaria (@raysan5) and contributors, zlib licensed. The lighting
;; and fog shaders follow the jolt port in jlt-commons/raylib-jlt. Altered from
;; the original: the run arguments and the `spin` mode are new here.
;;
;; See NOTICE.md at the root of this repository for the notices in full.

(ns lisp-games.mesh-instancing
  "Ten thousand lit cubes in a single draw call.

  Run it with `clojure -M:mesh`, or through this directory's `bb mesh`.

  Instancing moves the per-copy transform out of the draw loop and into a
  vertex attribute, so one mesh and one material go to the GPU alongside an
  array of N matrices and the vertex stage reads its own from
  `in mat4 instanceTransform`. raylib 6.0 resolves that attribute by name.

  Two modes, because the faithful example is a GPU showcase rather than a
  benchmark:

    static  the matrices are built once and the camera orbits, which is what
            raylib does. Per frame the CPU does almost nothing.
    spin    every matrix is rebuilt every frame, so the frame becomes
            per-instance arithmetic plus writes into foreign memory.

  What is specific to this port: raylib-clj already describes Mesh, Material,
  Shader, Matrix and Camera3D as coffi structs, so the by-value traffic needs
  no work here. What it does NOT bind is the four calls below, which are
  declared with coffi's own `defcfn` rather than patched into the library. The
  Pac-Man port in raylib-pacman does the same for its two.

  The matrix array is written with raw `mem/write-float` at word offsets, not
  through the `::matrix` struct alias, because the buffer is an array of
  matrices rather than one struct. raylib stores a Matrix as m0 m4 m8 m12 /
  m1 m5 m9 m13, which puts the translation at slots 3, 7 and 11."
  (:require
   [coffi.ffi :refer [defcfn]]
   [coffi.mem :as mem :refer [defalias]]
   [raylib.core.drawing :as rcd]
   [raylib.core.camera3d :as rc3]
   [raylib.core.timing :as rct]
   [raylib.core.window :as rcw]
   [raylib.models :as rm]
   [raylib.text.drawing :as rtd])
  (:gen-class))

;; --- the calls raylib-clj does not bind yet ----------------------------------
;; coffi's defcfn takes the C symbol, the argument types and the return type.

;; Shader and Material get their own aliases here rather than reusing
;; raylib-clj's. Its ::shader splits `int *locs` into two ints (:locs-lo and
;; :locs-hi) and its ::material FLATTENS the nested Shader into :shader-id
;; and :shader-locs. Both are valid layouts, but neither lets you hand a
;; Shader straight into a Material. These nest it, so swapping the instancing
;; shader in is one assoc.
;;
;; Getting this wrong is quiet: assoc a key the serializer does not know and
;; the material keeps the DEFAULT shader, which has no instanceTransform
;; attribute, so all N cubes render on top of each other at the origin.

(defalias ::shader
  [::mem/struct
   [[:id ::mem/int]
    [:_pad ::mem/int]          ; unsigned int then a pointer wants 8-byte align
    [:locs ::mem/pointer]]])

(defalias ::material
  [::mem/struct
   [[:shader ::shader]
    [:maps ::mem/pointer]
    [:param0 ::mem/float] [:param1 ::mem/float]
    [:param2 ::mem/float] [:param3 ::mem/float]]])

(defcfn load-material-default!
  "Load the default material"
  "LoadMaterialDefault" [] ::material)

(defcfn draw-mesh-instanced!
  "Draw multiple mesh instances with material and different transforms"
  {:arglists '([mesh material transforms instances])}
  "DrawMeshInstanced"
  [::rm/mesh ::material ::mem/pointer ::mem/int] ::mem/void)

(defcfn load-shader-from-memory!
  "Load shader from code strings and bind default locations"
  {:arglists '([vs-code fs-code])}
  "LoadShaderFromMemory"
  [::mem/c-string ::mem/c-string] ::shader)

(defcfn get-shader-location!
  "Get shader uniform location"
  {:arglists '([shader uniform-name])}
  "GetShaderLocation" [::shader ::mem/c-string] ::mem/int)

(defcfn set-shader-value!
  "Set shader uniform value"
  {:arglists '([shader loc-index value uniform-type])}
  "SetShaderValue" [::shader ::mem/int ::mem/pointer ::mem/int] ::mem/void)

(defcfn unload-shader!
  "Unload shader from GPU memory"
  {:arglists '([shader])}
  "UnloadShader" [::shader] ::mem/void)

(defcfn take-screenshot!
  "Take a screenshot of the current screen"
  {:arglists '([file-name])}
  "TakeScreenshot" [::mem/c-string] ::mem/void)

(defcfn draw-render-batch-active!
  "Update and draw internal render batch"
  "rlDrawRenderBatchActive" [] ::mem/void)

;; --- constants ---------------------------------------------------------------

(def W 800)
(def H 450)
(def SPREAD 52.0)
(def TAU 6.283185307179586)
(def ^:const FLAG-MSAA-4X-HINT 0x20)
(def ^:const SHADER-UNIFORM-FLOAT 0)
(def ^:const SHADER-UNIFORM-VEC3 2)
(def ^:const SHADER-UNIFORM-VEC4 3)
(def ^:const SHADER-UNIFORM-INT 4)

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

(defn matrix-set!
  "Write the instance matrix at index `i`: a rotation of `angle` about the unit
  axis, then a translation. Written field by field at word offsets, because the
  buffer is an array of matrices rather than a single struct."
  ;; No ^double hints on the parameters: Clojure supports primitive args only
  ;; up to arity 4 and this takes nine, so hinting them fails to compile with
  ;; "fns taking primitives support only 4 or fewer args". The arithmetic
  ;; below still runs on primitive doubles once they are bound.
  [buf i ax ay az angle tx ty tz]
  (let [base (* 64 i)
        c (Math/cos angle)
        s (Math/sin angle)
        t (- 1.0 c)
        w (fn [slot v] (mem/write-float buf (+ base (* 4 slot)) (float v)))]
    (w 0 (+ (* t ax ax) c))        (w 1 (- (* t ax ay) (* s az)))
    (w 2 (+ (* t ax az) (* s ay))) (w 3 tx)
    (w 4 (+ (* t ax ay) (* s az))) (w 5 (+ (* t ay ay) c))
    (w 6 (- (* t ay az) (* s ax))) (w 7 ty)
    (w 8 (- (* t ax az) (* s ay))) (w 9 (+ (* t ay az) (* s ax)))
    (w 10 (+ (* t az az) c))       (w 11 tz)
    (w 12 0.0) (w 13 0.0) (w 14 0.0) (w 15 1.0)))

(defn scatter
  "One random placement per instance, generated once whatever the mode."
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

(defn fill-transforms!
  "Every instance's matrix at time `t`. The whole of spin mode's frame cost."
  [buf places t]
  (dotimes [i (count places)]
    (let [[ax ay az a0 tx ty tz] (nth places i)]
      (matrix-set! buf i ax ay az (+ a0 t) tx ty tz)))
  buf)

;; --- uniforms ----------------------------------------------------------------

(defn set-floats!
  "A float uniform of `n` components. raylib-clj's own setters take ITS
  ::shader layout, which splits `int *locs` into two ints, so this port
  carries its own rather than converting between the two representations."
  [sh name kind vals]
  (let [buf (mem/alloc (* 4 (count vals)))]
    (dotimes [i (count vals)]
      (mem/write-float buf (* 4 i) (float (nth vals i))))
    (set-shader-value! sh (get-shader-location! sh name) buf kind)))

(defn set-int! [sh name v]
  (let [buf (mem/alloc 4)]
    (mem/write-int buf 0 (int v))
    (set-shader-value! sh (get-shader-location! sh name) buf SHADER-UNIFORM-INT)))

(defn light! [sh i kind [px py pz] [r g b]]
  (set-int! sh (str "lights[" i "].enabled") 1)
  (set-int! sh (str "lights[" i "].type") kind)
  (set-floats! sh (str "lights[" i "].position") SHADER-UNIFORM-VEC3 [px py pz])
  (set-floats! sh (str "lights[" i "].target") SHADER-UNIFORM-VEC3 [0.0 0.0 0.0])
  (set-floats! sh (str "lights[" i "].color") SHADER-UNIFORM-VEC4
               [(/ r 255.0) (/ g 255.0) (/ b 255.0) 1.0]))

;; --- the loop ----------------------------------------------------------------

(defn -main
  "Open a window and run until closed.

  clojure -M:mesh                          until the window is closed
  clojure -M:mesh 10                       quit after ten seconds of frame time
  clojure -M:mesh 5 out.png 40             quit after five, capture frame 40
  clojure -M:mesh 5 out.png 40 2000        the same, with 2000 instances
  clojure -M:mesh 5 out.png 40 2000 spin   rebuild every matrix every frame"
  [& args]
  (let [[secs shot-path shot-frame n mode] args
        deadline (some-> secs parse-long)
        shot-frame (or (some-> shot-frame parse-long) 40)
        n (or (some-> n parse-long) 10000)
        spin? (= "spin" mode)]
    (rcw/set-config-flags! FLAG-MSAA-4X-HINT)
    (rcw/init-window! W H "lisp-games - mesh instancing")
    (rct/set-target-fps! 120)
    (let [sh (load-shader-from-memory! vertex-shader fragment-shader)
          mat (assoc (load-material-default!) :shader sh)
          cube (rm/gen-mesh-cube 0.85 0.85 0.85)
          buf (mem/alloc (* 64 n))
          places (scatter n)
          view-pos (get-shader-location! sh "viewPos")]
      (fill-transforms! buf places 0.0)
      (set-floats! sh "ambient" SHADER-UNIFORM-VEC4 [0.35 0.35 0.4 1.0])
      (set-floats! sh "fogColor" SHADER-UNIFORM-VEC4 [0.09 0.10 0.14 1.0])
      (set-floats! sh "fogDensity" SHADER-UNIFORM-FLOAT [0.028])
      (light! sh 0 1 [22.0 26.0 22.0] [255 244 220])
      (light! sh 1 1 [-24.0 -14.0 -18.0] [90 150 255])
      (set-int! sh "lights[2].enabled" 0)
      (set-int! sh "lights[3].enabled" 0)
      (loop [frame 0 t 0.0 frames 0 acc 0.0 b-acc 0.0 d-acc 0.0 hud "measuring..."]
        (when (and (not (rcw/window-should-close?))
                   (or (nil? deadline) (< t deadline)))
          (let [dt (min 0.05 (double (rct/get-frame-time)))
                t2 (+ t dt)
                cam-x (* 46.0 (Math/sin (* t2 0.25)))
                cam-z (* 46.0 (Math/cos (* t2 0.25)))
                b0 (System/nanoTime)
                _ (when spin? (fill-transforms! buf places (* t2 0.5)))
                b1 (System/nanoTime)]
            (let [vp (mem/alloc 12)]
              (mem/write-float vp 0 (float cam-x))
              (mem/write-float vp 4 (float 12.0))
              (mem/write-float vp 8 (float cam-z))
              (set-shader-value! sh view-pos vp SHADER-UNIFORM-VEC3))
            (rcd/begin-drawing!)
            (rcd/clear-background! {:r 23 :g 26 :b 36 :a 255})
            (rc3/begin-mode-3d! {:position {:x cam-x :y 12.0 :z cam-z}
                                 :target {:x 0.0 :y 0.0 :z 0.0}
                                 :up {:x 0.0 :y 1.0 :z 0.0}
                                 :fovy 45.0 :projection 0})
            (let [d0 (System/nanoTime)
                  _ (draw-mesh-instanced! cube mat buf n)
                  d1 (System/nanoTime)]
              (rc3/end-mode-3d!)
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
                (rtd/draw-text! "raylib [shaders] example - mesh instancing" 16 14 20
                                {:r 245 :g 245 :b 245 :a 255})
                (rtd/draw-text! (str n " lit cubes in one draw call") 16 40 14
                                {:r 165 :g 180 :b 210 :a 255})
                (rtd/draw-text! hud2 16 (- H 30) 16 {:r 165 :g 180 :b 210 :a 255})
                (when (and shot-path (= frame shot-frame))
                  (draw-render-batch-active!)
                  (take-screenshot! shot-path))
                (rcd/end-drawing!)
                (recur (inc frame) t2
                       (if refresh? 0 frames2) (if refresh? 0.0 acc2)
                       (if refresh? 0.0 b-acc2) (if refresh? 0.0 d-acc2)
                       hud2))))))
      (rm/unload-mesh! cube)
      (unload-shader! sh))
    (rcw/close-window!)))
