;; Helitorus, after Michiel Borkent's (@borkdude) examples/helitorus.clj in
;; babashka/ffi: https://github.com/babashka/ffi/blob/main/examples/helitorus.clj
;;
;; That example is the original and this file is a port of it onto raylib-clj,
;; by way of the babashka port next door. The geometry, the painter ordering,
;; the backface test, the lighting and the palette are his and unchanged. His
;; own header credits a Scittle canvas demo one step further back.
;;
;; babashka/ffi is MIT licensed, Copyright (c) 2026 Michiel Borkent; see
;; NOTICE.md at the root of this repository for the notice in full.

(ns lisp-games.helitorus
  "A helix of n windings around a torus, swept into a tube.

  Run it with `clojure -M:helitorus`, or through this directory's
  `bb helitorus`. Drag to turn, wheel to zoom, LEFT/RIGHT change the winding
  count and UP/DOWN the resolution along the spine.

  This is the compute-heavy one. Every frame rebuilds the whole surface,
  projects it, shades it and depth-sorts the rings, so unlike Pac-Man the
  runtime is doing real work between frames. The HUD reports compute and draw
  milliseconds separately, which is the number worth comparing between
  runtimes rather than guessing at.

  What is specific to this port: raylib-clj binds raylib through coffi on
  Panama and hands structs across as ordinary Clojure maps, so a Color is
  {:r :g :b :a} rather than a packed integer. It does not bind rlgl, though,
  and this program lives in rlgl immediate mode, so the six calls it needs are
  declared below with coffi's own `defcfn`. That is the same escape hatch the
  Pac-Man port in raylib-pacman uses for its two, and it is a small amount of
  code because coffi wants nothing but the C symbol and the types.

  The surface goes out one rlgl batch per ring, which is not optional: rlgl
  cannot flush inside an open rlBegin/rlEnd, and the whole figure overflows
  the vertex buffer in one go."
  (:require
   [coffi.ffi :refer [defcfn]]
   [coffi.mem :as mem]
   [raylib.core.drawing :as rcd]
   [raylib.core.keyboard :as rck]
   [raylib.core.mouse :as rcm]
   [raylib.core.timing :as rct]
   [raylib.core.window :as rcw]
   [raylib.enums :as enums]
   [raylib.text.drawing :as rtd])
  (:gen-class))

;; --- the calls raylib-clj does not bind yet ----------------------------------
;; rlgl is raylib's lower-level immediate-mode layer. raylib-clj binds the
;; high-level API, so these six are declared here rather than patched into the
;; library, which keeps the example self-contained. coffi's `defcfn` takes the
;; C symbol, the argument types and the return type, and nothing else.

(defcfn rl-begin!
  "Initialize drawing using a given primitive mode"
  "rlBegin" [::mem/int] ::mem/void)

(defcfn rl-end!
  "Finish the vertex batch opened by rlBegin"
  "rlEnd" [] ::mem/void)

(defcfn rl-vertex-2f!
  "Define one vertex in the current batch"
  "rlVertex2f" [::mem/float ::mem/float] ::mem/void)

(defcfn rl-color-4ub!
  "Set the colour for subsequent vertices, as four bytes"
  "rlColor4ub" [::mem/int ::mem/int ::mem/int ::mem/int] ::mem/void)

(defcfn rl-disable-backface-culling!
  "Stop raylib culling back faces, because this example decides visibility itself"
  "rlDisableBackfaceCulling" [] ::mem/void)

(defcfn rl-draw-render-batch-active!
  "Force the pending batch out, so a mid-frame screenshot sees the geometry"
  "rlDrawRenderBatchActive" [] ::mem/void)

(defcfn take-screenshot!
  "Take a screenshot of the current screen"
  {:arglists '([file-name])}
  "TakeScreenshot" [::mem/c-string] ::mem/void)

;; --- constants ---------------------------------------------------------------

(def RL-TRIANGLES 4)

(def KEY-RIGHT (:right enums/keyboard-key))
(def KEY-LEFT (:left enums/keyboard-key))
(def KEY-UP (:up enums/keyboard-key))
(def KEY-DOWN (:down enums/keyboard-key))
(def MOUSE-LEFT (:left enums/mouse-button))

;; Colours are maps here, which is the whole point of the coffi binding. The
;; one exception is the surface itself: rlColor4ub takes four bytes, so the
;; palette stays as three int arrays and is submitted component by component.
(def BACKGROUND {:r 255 :g 255 :b 255 :a 255})
(def HUD-COLOR {:r 55 :g 65 :b 81 :a 255})
(def HELP-COLOR {:r 156 :g 163 :b 175 :a 255})
(def HELP-TEXT
  "drag to turn, wheel to zoom, LEFT/RIGHT windings, UP/DOWN resolution")

(def W 1000)
(def H 560)
(def CX (/ W 2.0))
(def CY (/ H 2.0))

(def NV 12)          ; points around the tube cross-section
(def MAX-NU 900)     ; max points along the spine
(def R 1.0)          ; major radius
(def r2 0.30)        ; minor radius of the torus
(def dist 4.6)
(def focal 3.2)
(def TAU 6.283185307179586)

;; --- run state ---------------------------------------------------------------

(def NU (atom 260))
(def twists (atom 14))
(def r3 (atom 0.11))
(def rot-x (atom 0.55))
(def rot-y (atom 0.0))
(def vel-x (atom 0.0))
(def vel-y (atom 0.45))
(def zoom (atom 250.0))
(def dragging (atom false))


;; goes through java.lang.reflect.Array in bb and costs ~6.7us per write
;; (vs ~37ns typed), which dominated this loop.
;;
;; On the JVM the same concern has a different spelling: the ^"[D" and
;; ^"[I" hints below are load-bearing, not decorative. Without them `aget`
;; cannot resolve at the call site and every read goes through
;; clojure.lang.RT reflectively. Measured on this file: 21 ms compute and
;; 78 ms draw per frame unhinted. Note the hint must be the array class,
;; ^"[D" not ^doubles: on a def the latter resolves to clojure.core/doubles,
;; the function, and the namespace fails to compile. The jolt port answers
;; the same problem
;; with ^double/1 and ^int/1.
;; the phi grid is fixed, only its phase moves
(def ^"[D" cos-phi (double-array NV))
(def ^"[D" sin-phi (double-array NV))
(dotimes [j NV]
  (let [a (/ (* TAU j) NV)]
    (aset-double cos-phi j (Math/cos a))
    (aset-double sin-phi j (Math/sin a))))

(def ^"[D" sx (double-array (* MAX-NU NV)))
(def ^"[D" sy (double-array (* MAX-NU NV)))
(def ^"[I" shade (int-array (* MAX-NU NV)))
(def ^"[D" ring-z (double-array MAX-NU))
;; kept between frames: stays almost sorted, so the insertion sort is cheap
(def ^"[I" order (int-array MAX-NU))
(def order-nu (atom 0))

(defn reset-order! [nu]
  (dotimes [i nu] (aset-int order i i))
  (reset! order-nu nu))

;; hsl(337..349, 100..78%, 17..72%) as packed rgba, the Scittle palette
(defn hsl->rgba [h s l]
  (let [c (* (- 1.0 (Math/abs (- (* 2.0 l) 1.0))) s)
        h' (/ h 60.0)
        x (* c (- 1.0 (Math/abs (- (mod h' 2.0) 1.0))))
        m (- l (/ c 2.0))
        [r g b] (cond
                  (< h' 1) [c x 0.0] (< h' 2) [x c 0.0] (< h' 3) [0.0 c x]
                  (< h' 4) [0.0 x c] (< h' 5) [x 0.0 c] :else [c 0.0 x])]
    [(int (* 255 (+ r m))) (int (* 255 (+ g m))) (int (* 255 (+ b m)))]))

(def ^"[I" palette-r (int-array 64))
(def ^"[I" palette-g (int-array 64))
(def ^"[I" palette-b (int-array 64))
(dotimes [i 64]
  (let [t (/ i 63.0)
        [r g b] (hsl->rgba (+ 337.0 (* 12.0 t)) (/ (- 100.0 (* 22.0 t)) 100.0)
                           (/ (+ 17.0 (* 55.0 t)) 100.0))]
    (aset-int palette-r i r) (aset-int palette-g i g) (aset-int palette-b i b)))

(defn compute! [t]
  (let [nu (long @NU)
        n (long @twists)
        rr3 (double @r3)
        r (+ r2 rr3)
        zm (double @zoom)
        ay (double @rot-y)
        ax (double @rot-x)
        cay (Math/cos ay) say (Math/sin ay)
        cax (Math/cos ax) sax (Math/sin ax)
        po (* t 1.1)
        cpo (Math/cos po)
        spo (Math/sin po)
        dtheta (/ TAU nu)]
    (when (not= nu @order-nu) (reset-order! nu))
    (loop [i 0]
      (when (< i nu)
        (let [th (* i dtheta)
              nth (* n th)
              cn (Math/cos nth) sn (Math/sin nth)
              ct (Math/cos th) st (Math/sin th)
              xr (+ R (* r cn))
              ;; spine
              px (* xr ct) py (* xr st) pz (* r sn)
              ;; spine tangent: the theta derivative, written out
              tx (- (* (- xr) st) (* n r ct sn))
              ty (- (* xr ct) (* n r st sn))
              tz (* n r cn)
              ;; tangent of the flat circle under the spine
              bx (- st) by ct
              ;; tube normal: t cross b, normalised
              nx (- (* ty 0.0) (* tz by))
              ny (- (* tz bx) (* tx 0.0))
              nz (- (* tx by) (* ty bx))
              nl (Math/sqrt (+ (* nx nx) (* ny ny) (* nz nz)))
              nx (/ nx nl) ny (/ ny nl) nz (/ nz nl)
              ;; third axis: n cross t, normalised
              ux (- (* ny tz) (* nz ty))
              uy (- (* nz tx) (* nx tz))
              uz (- (* nx ty) (* ny tx))
              ul (Math/sqrt (+ (* ux ux) (* uy uy) (* uz uz)))
              ux (/ ux ul) uy (/ uy ul) uz (/ uz ul)
              base (* i NV)]
          ;; ring depth, for the painter's ordering below
          (let [rz (+ (* px say) (* pz cay))]
            (aset-double ring-z i (- (* rz cax) (* py sax))))
          (loop [j 0]
            (when (< j NV)
              (let [c0 (aget cos-phi j)
                    s0 (aget sin-phi j)
                    cp (- (* c0 cpo) (* s0 spo))
                    sp (+ (* s0 cpo) (* c0 spo))
                    ;; surface normal, then the point
                    vx (+ (* ux cp) (* nx sp))
                    vy (+ (* uy cp) (* ny sp))
                    vz (+ (* uz cp) (* nz sp))
                    wx (+ px (* rr3 vx))
                    wy (+ py (* rr3 vy))
                    wz (+ pz (* rr3 vz))
                    ;; rotate about Y, then about X
                    x1 (- (* wx cay) (* wz say))
                    z1 (+ (* wx say) (* wz cay))
                    y2 (+ (* wy cax) (* z1 sax))
                    z2 (- (* z1 cax) (* wy sax))
                    ;; same rotation on the normal
                    m1 (- (* vx cay) (* vz say))
                    q1 (+ (* vx say) (* vz cay))
                    m2 (+ (* vy cax) (* q1 sax))
                    q2 (- (* q1 cax) (* vy sax))
                    k (/ (* zm focal) (+ focal dist z2))
                    ;; diffuse plus a rim term
                    lum (+ (* 0.60 (max 0.0 (+ (* m1 -0.40) (* m2 -0.62) (* q2 -0.68))))
                           (* 0.28 (max 0.0 (- q2)))
                           0.12)
                    idx (min 63 (max 0 (int (* 63.0 lum))))
                    o (+ base j)]
                (aset-double sx o (+ CX (* x1 k)))
                (aset-double sy o (- CY (* y2 k)))
                (aset-int shade o idx))
              (recur (inc j))))
          (recur (inc i)))))
    ;; far rings first
    (loop [i 1]
      (when (< i nu)
        (let [v (aget order i)
              vz (aget ring-z v)]
          (loop [k (dec i)]
            (if (and (>= k 0) (< (aget ring-z (aget order k)) vz))
              (do (aset-int order (inc k) (aget order k))
                  (recur (dec k)))
              (aset-int order (inc k) v))))
        (recur (inc i))))))

;; one flat-shaded quad per cell as two rlgl triangles, back faces dropped by
;; the sign of the 2D cross product
(defn- vert!
  "One vertex. coffi declares rlVertex2f as ::mem/float, and everything in the
  arrays above is a double, so the coercion is explicit rather than implicit.
  Passing a double straight through is a type error, not a silent narrowing."
  [x y]
  (rl-vertex-2f! (float x) (float y)))

(defn draw! []
  (let [nu (long @NU)]
    (loop [oi 0]
      (when (< oi nu)
        ;; one batch per ring: rlgl cannot flush inside an open rlBegin/rlEnd,
        ;; and the whole surface overflows its vertex buffer
        (rl-begin! RL-TRIANGLES)
        (let [i (aget order oi)
              i2 (let [x (inc i)] (if (= x nu) 0 x))
              b1 (* i NV)
              b2 (* i2 NV)]
          (loop [j 0]
            (when (< j NV)
              (let [j2 (let [x (inc j)] (if (= x NV) 0 x))
                    a (+ b1 j) b (+ b1 j2)
                    c (+ b2 j2) d (+ b2 j)
                    xa (aget sx a) ya (aget sy a)
                    xb (aget sx b) yb (aget sy b)
                    xc (aget sx c) yc (aget sy c)]
                (when (pos? (- (* (- xb xa) (- yc ya))
                               (* (- yb ya) (- xc xa))))
                  (let [s (aget shade a)]
                    (rl-color-4ub! (aget palette-r s) (aget palette-g s) (aget palette-b s) 255))
                  (vert! xa ya) (vert! xb yb) (vert! xc yc)
                  (vert! xa ya) (vert! xc yc)
                  (vert! (aget sx d) (aget sy d))))
              (recur (inc j)))))
        (rl-end!)
        (recur (inc oi))))))

;; --- input -------------------------------------------------------------------
;; This is where raylib-clj reads differently from the scalar FFIs. Its
;; predicates return real booleans rather than a C uint8, so there is no
;; `(pos? ...)` wrapper on any of them, and the key constants come from
;; `enums/keyboard-key` rather than being spelled as integers.

(def last-px (atom 0))
(def last-py (atom 0))

(defn advance-camera! [dt]
  (if (rcm/is-mouse-button-down? MOUSE-LEFT)
    (let [x (rcm/get-mouse-x) y (rcm/get-mouse-y)]
      (when @dragging
        (let [dx (- x @last-px) dy (- y @last-py)]
          (swap! rot-y + (* dx 0.008))
          (swap! rot-x (fn [v] (max -1.45 (min 1.45 (+ v (* dy 0.008))))))
          (reset! vel-y (* dx 0.35))
          (reset! vel-x (* dy 0.35))))
      (reset! dragging true)
      (reset! last-px x)
      (reset! last-py y))
    (do
      (reset! dragging false)
      ;; releasing keeps the spin and decays it to an idle turn
      (let [decay (Math/pow 0.94 (/ dt 0.016))
            vy (+ (* @vel-y decay) (* 0.16 (- 1.0 decay)))]
        (reset! vel-y vy)
        (reset! vel-x (* @vel-x decay)))))
  (swap! rot-y + (* @vel-y dt))
  (swap! rot-x (fn [v] (max -1.45 (min 1.45 (+ v (* @vel-x dt)))))))

(defn handle-keys! []
  (let [w (rcm/get-mouse-wheel-move)]
    (when-not (zero? w)
      (swap! zoom (fn [z] (max 110.0 (min 520.0 (* z (Math/exp (* 0.09 w)))))))))
  (when (rck/is-key-pressed? KEY-RIGHT) (swap! twists #(min 24 (inc %))))
  (when (rck/is-key-pressed? KEY-LEFT) (swap! twists #(max 3 (dec %))))
  (when (rck/is-key-down? KEY-UP) (swap! NU #(min MAX-NU (+ % 4))))
  (when (rck/is-key-down? KEY-DOWN) (swap! NU #(max 60 (- % 4)))))

;; --- the loop ----------------------------------------------------------------
;; Per-run, not load-time, so `clojure -M:check` can load this namespace
;; without opening a window or starting a clock.

(def frames (atom 0))
(def acc (atom 0.0))
(def c-acc (atom 0.0))
(def d-acc (atom 0.0))
(def hud (atom "computing..."))
(def frames-total (atom 0))

(defn -main
  "Open a window and run until closed.

  clojure -M:helitorus                    run until the window is closed
  clojure -M:helitorus 10                 quit after ten seconds of frame time
  clojure -M:helitorus 3 out.png 40       quit after three, capture frame 40
  clojure -M:helitorus 3 out.png 40 400   the same, starting at resolution 400"
  [& args]
  (let [[secs shot-path shot-frame res] args
        deadline (some-> secs parse-long)
        shot-frame (or (some-> shot-frame parse-long) 40)]
    (when-let [n (some-> res parse-long)]
      (reset! NU (max 60 (min MAX-NU n))))
    (rcw/init-window! W H "lisp-games - helitorus")
    (rct/set-target-fps! 120)
    (rl-disable-backface-culling!)
    (loop [t 0.0]
      ;; The deadline counts GAME time, summed from the clamped per-frame dt,
      ;; rather than wall time, so a runtime that pays a one-off cost before
      ;; the first frame does not spend its whole budget there.
      (when (and (not (rcw/window-should-close?))
                 (or (nil? deadline) (< t deadline)))
        (let [dt (min 0.05 (double (rct/get-frame-time)))
              t (+ t dt)]
          (handle-keys!)
          (advance-camera! dt)
          (let [c0 (System/nanoTime)]
            (compute! t)
            (swap! c-acc + (/ (- (System/nanoTime) c0) 1e6)))
          (rcd/begin-drawing!)
          (rcd/clear-background! BACKGROUND)
          (let [d0 (System/nanoTime)]
            (draw!)
            (swap! d-acc + (/ (- (System/nanoTime) d0) 1e6)))
          (swap! frames inc)
          (swap! acc + dt)
          (when (>= @acc 0.4)
            (let [f @frames]
              (reset! hud (format "fps %.0f | compute %.1f ms | draw %.1f ms | %dk verts/s | windings %d | res %d"
                                  (/ f @acc) (/ @c-acc f) (/ @d-acc f)
                                  (int (/ (* (/ f @acc) @NU NV) 1000))
                                  @twists @NU))
              (println @hud))
            (reset! frames 0) (reset! acc 0.0) (reset! c-acc 0.0) (reset! d-acc 0.0))
          (rtd/draw-text! @hud 12 12 20 HUD-COLOR)
          (rtd/draw-text! HELP-TEXT 12 (- H 28) 16 HELP-COLOR)
          ;; raylib defers batched geometry until EndDrawing, so a screenshot
          ;; taken here sees only the cleared background unless the batch is
          ;; forced out first.
          (when (and shot-path (= shot-frame @frames-total))
            (rl-draw-render-batch-active!)
            (take-screenshot! shot-path))
          (swap! frames-total inc)
          (rcd/end-drawing!)
          (recur t))))
    (rcw/close-window!)))
