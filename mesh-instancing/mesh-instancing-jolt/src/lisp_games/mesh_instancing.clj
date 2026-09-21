;; Mesh instancing, after raylib's examples/shaders/shaders_mesh_instancing.c
;; by Ramon Santamaria (@raysan5) and contributors, zlib licensed. This port
;; came by way of the `mesh-instancing` example in jlt-commons/raylib-jlt,
;; which is itself a port of the same C original. Altered from both: the run
;; arguments and the `spin` mode are new here.
;;
;; See NOTICE.md at the root of this repository for the notices in full.

(ns lisp-games.mesh-instancing
  "Ten thousand lit cubes in a single draw call.

  Run it with `jolt -M:mesh`, or through this directory's `bb mesh`.

  Instancing moves the per-copy transform out of the draw loop and into a
  vertex attribute. One mesh and one material go to the GPU alongside an array
  of N matrices, and the vertex stage reads its own matrix per instance from
  `in mat4 instanceTransform`. raylib 6.0 resolves that attribute by name when
  the shader loads, so nothing has to be wired up for it.

  Two modes, because the faithful example is a GPU showcase rather than a
  benchmark:

    static  the matrices are built once and the camera orbits, which is what
            raylib does. Per frame the CPU does almost nothing, so the number
            describes your GPU rather than this runtime.
    spin    every matrix is rebuilt every frame, so the frame becomes
            per-instance arithmetic plus marshalling into foreign memory.

  What is specific to this port: the raylib-jlt wrapper already carries the
  struct-by-value work. A Mesh and a Material cross into DrawMeshInstanced by
  value, and `matrix-array-set!` writes one 64-byte Matrix at an index,
  field by field, because raylib stores a Matrix as m0 m4 m8 m12 / m1 m5 m9
  m13 and so on, which puts the translation at word offsets 3, 7 and 11."
  (:require
   [net.b12n.raylib.all :as rl]))

(def ^:const W 800)
(def ^:const H 450)
(def ^:const SPREAD 52.0)
(def TAU (* 2.0 Math/PI))

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


;; --- placements --------------------------------------------------------------

(defn scatter
  "One random placement per instance: a position in a cube of side SPREAD, a
  unit axis and a starting angle. Generated once, whatever the mode."
  [n]
  (vec (for [_ (range n)]
         (let [ax (+ 0.1 (rand 1.0))
               ay (+ 0.1 (rand 1.0))
               az (+ 0.1 (rand 1.0))
               len (Math/sqrt (+ (* ax ax) (* ay ay) (* az az)))]
           [[(/ ax len) (/ ay len) (/ az len)]
            (rand TAU)
            [(- (rand SPREAD) (/ SPREAD 2.0))
             (- (rand SPREAD) (/ SPREAD 2.0))
             (- (rand SPREAD) (/ SPREAD 2.0))]]))))

(defn fill-transforms!
  "Write every instance's matrix at time `t`. This is the whole of `spin`
  mode's per-frame cost."
  [buf places t]
  (dotimes [i (count places)]
    (let [[axis a0 pos] (nth places i)]
      (rl/matrix-array-set! buf i axis (+ a0 t) pos)))
  buf)

(defn- light!
  [sh i kind [px py pz] [r g b]]
  (rl/set-uniform-int! sh (rl/uniform-loc sh (str "lights[" i "].enabled")) 1)
  (rl/set-uniform-int! sh (rl/uniform-loc sh (str "lights[" i "].type")) kind)
  (rl/set-uniform-vec3! sh (rl/uniform-loc sh (str "lights[" i "].position")) px py pz)
  (rl/set-uniform-vec3! sh (rl/uniform-loc sh (str "lights[" i "].target")) 0.0 0.0 0.0)
  (rl/set-uniform-vec4! sh (rl/uniform-loc sh (str "lights[" i "].color"))
                        (/ r 255.0) (/ g 255.0) (/ b 255.0) 1.0))

;; --- the loop ----------------------------------------------------------------

(defn -main
  "Open a window and run until closed.

  jolt -M:mesh                          until the window is closed
  jolt -M:mesh 10                       quit after ten seconds of frame time
  jolt -M:mesh 5 out.png 40             quit after five, capture frame 40
  jolt -M:mesh 5 out.png 40 2000        the same, with 2000 instances
  jolt -M:mesh 5 out.png 40 2000 spin   rebuild every matrix every frame"
  [& args]
  (let [[secs shot-path shot-frame n mode] args
        deadline (some-> secs parse-long)
        shot-frame (or (some-> shot-frame parse-long) 40)
        n (or (some-> n parse-long) 10000)
        spin? (= "spin" mode)]
    (rl/set-config-flags rl/FLAG-MSAA-4X-HINT)
    (rl/window! {:width W :height H :title "lisp-games - mesh instancing"})
    (rl/set-target-fps 120)
    (let [sh (rl/shader-vf vertex-shader fragment-shader)]
      (if-not sh
        (binding [*out* *err*]
          (println "mesh-instancing: the instancing shader did not link (log above)"))
        (let [material (doto (rl/material-default)
                         (rl/material-shader! sh)
                         (rl/material-diffuse-color! (rl/rgba 210 215 235 255)))
              cube (rl/mesh-alloc)
              transforms (rl/matrix-array-alloc n)
              places (scatter n)
              view-pos (rl/uniform-loc sh "viewPos")]
          (rl/mesh-cube! cube 0.85 0.85 0.85)
          (fill-transforms! transforms places 0.0)
          (rl/set-uniform-vec4! sh (rl/uniform-loc sh "ambient") 0.35 0.35 0.4 1.0)
          (rl/set-uniform-vec4! sh (rl/uniform-loc sh "fogColor") 0.09 0.10 0.14 1.0)
          (rl/set-uniform-float! sh (rl/uniform-loc sh "fogDensity") 0.028)
          (light! sh 0 1 [22.0 26.0 22.0] [255 244 220])
          (light! sh 1 1 [-24.0 -14.0 -18.0] [90 150 255])
          (rl/set-uniform-int! sh (rl/uniform-loc sh "lights[2].enabled") 0)
          (rl/set-uniform-int! sh (rl/uniform-loc sh "lights[3].enabled") 0)
          (loop [frame 0 t 0.0 frames 0 acc 0.0 b-acc 0.0 d-acc 0.0
                 hud "measuring..."]
            (when (and (not (rl/window-should-close?))
                       (or (nil? deadline) (< t deadline)))
              (let [dt (min 0.05 (rl/get-frame-time))
                    t2 (+ t dt)
                    cam-x (* 46.0 (Math/sin (* t2 0.25)))
                    cam-z (* 46.0 (Math/cos (* t2 0.25)))
                    b0 (System/nanoTime)
                    _ (when spin? (fill-transforms! transforms places (* t2 0.5)))
                    b1 (System/nanoTime)]
                (rl/set-uniform-vec3! sh view-pos cam-x 12.0 cam-z)
                (rl/begin-drawing)
                (rl/clear-background (rl/rgba 23 26 36 255))
                (let [d0 (System/nanoTime)]
                  (rl/with-camera-3d
                    {:pos-x cam-x :pos-y 12.0 :pos-z cam-z
                     :target-x 0.0 :target-y 0.0 :target-z 0.0
                     :up-x 0.0 :up-y 1.0 :up-z 0.0
                     :fovy 45.0}
                    (fn [] (rl/draw-mesh-instanced! cube material transforms n)))
                  (let [d1 (System/nanoTime)
                        frames2 (inc frames)
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
                    (rl/text! "raylib [shaders] example - mesh instancing"
                              {:x 16 :y 14 :size 20 :color rl/RAYWHITE})
                    (rl/text! (str n " lit cubes in one draw call")
                              {:x 16 :y 40 :size 14 :color (rl/rgba 165 180 210 255)})
                    (rl/text! hud2 {:x 16 :y (- H 30) :size 16
                                    :color (rl/rgba 165 180 210 255)})
                    (when (and shot-path (= frame shot-frame))
                      (rl/flush-batch)
                      (rl/take-screenshot shot-path))
                    (rl/end-drawing)
                    (recur (inc frame) t2
                           (if refresh? 0 frames2) (if refresh? 0.0 acc2)
                           (if refresh? 0.0 b-acc2) (if refresh? 0.0 d-acc2)
                           hud2))))))
          (rl/unload-mesh! cube)
          (rl/mesh-free! cube)
          (rl/matrix-free! transforms)
          (rl/material-free! material)
          (rl/unload-shader! sh))))
    (rl/close-window)))
