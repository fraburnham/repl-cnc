(ns repl-cnc.shapes.primitives
  (:require [repl-cnc.gcode :as gcode]))

(defn next-stepover
  [tool-width stepover-amount distance-remaining]
  (if (> (* stepover-amount tool-width) distance-remaining)
    distance-remaining
    (* tool-width stepover-amount)))

(defn stepovers
  "Calculate the relative moves needed for a given tool config to reach but not exceed end point
   stepover amount should always be positive"
  [tool-width stepover-amount end]
  {:pre [(pos? tool-width)]}
  (let [sign (if (pos? end) + -)
        end (Math/abs end)
        stepover-amount (Math/abs stepover-amount)]
    (loop [stepovers [0]
           distance-remaining (- end tool-width)]
      (if (or (zero? distance-remaining)
              (neg? distance-remaining))
        stepovers
        (let [stepover (next-stepover tool-width stepover-amount distance-remaining)]
          (recur (conj stepovers
                       (sign stepover))
                 (- distance-remaining stepover)))))))

(defn stepdowns ; there is probably a way to consolidate stepovers and stepdowns. I need to sort that out.
  "Calculate the relative moves needed for a given tool config to reach but not exceed end point
   stepover amount should always be positive"
  [plunge-depth end]
  {:pre [(pos? plunge-depth)]}
  (let [sign (if (pos? end) + -)
        end (Math/abs end)]
    (loop [stepdowns [] ; Don't need the 0th step for step downs. Assume we're _above_ the material to start
           distance-remaining end]
      (if (or (zero? distance-remaining)
              (neg? distance-remaining))
        stepdowns
        (let [stepdown (next-stepover plunge-depth 1 distance-remaining)]
          (recur (conj stepdowns
                       (sign stepdown))
                 (- distance-remaining stepdown)))))))

(defn end-offset
  "End accounting for tool width"
  [tool-width end]
  (if (zero? end)
    end
    (- end tool-width)))

(defn square-surface
  [config x-end y-end stepover-amount]
  (let [adj-x-end ((if (pos? x-end) - +) x-end (:tool-width config))]
    (mapcat (fn [y-step x-direction]
              [(gcode/relative-move config 0 y-step 0)
               (gcode/relative-move config (x-direction adj-x-end) 0 0)])
            (stepovers (:tool-width config) stepover-amount y-end)
            (interleave (repeat +) (repeat -)))))

(defn square-pocket
  "Recursively build the box hole steps assuming bounds are max limits for the outside of the tool"
  [config x-end y-end z-end stepover-amount]
  (let [surface-moves (square-surface config x-end y-end stepover-amount)]
    (reduce (fn [steps z-step]
              (-> (into steps [(gcode/relative-move config 0 0 z-step)])
                  (into surface-moves)
                  (conj (gcode/absolute-move config 0 0 nil))))
            [gcode/local-home]
            (stepdowns (:plunge-depth config) z-end))))

(defn arc-pocket
  "Doesn't account for any home reference except bottom for now"
  [config radius z-end]
  ;; There is a gcode to change the plane for the arc! 2.5d repl carving
  (let [stepdowns (stepdowns (Math/abs (:plunge-depth config)) z-end)]
    (-> (reduce
         (fn [r stepdown]
           (conj r
                 (gcode/plunge config stepdown)
                 (gcode/arc config 0 0 0 radius)))
         []
         stepdowns)
        (conj (gcode/relative-move config 0 0 (- z-end))))))

(defn radius-scale
  "Return a seq that has the correct radius for each stepover step"
  ([stepovers]
   (radius-scale stepovers 0))
  ([stepovers offset]
   (->> (reduce
         (fn [radii stepover-distance]
           (conj radii (+
                        (last radii)
                        stepover-distance)))
         [0]
         (rest stepovers)) ; ugh more hacky shit. Something is horribly wrong in here
        (map #(- (+ offset %))))))

(defn circle-pocket
  "The start of the hole is centroid"
  [config diameter z-end stepover-amount]
  ;; stepovers don't really work for circles because of the radius center instead of the edge of the tool being center
  ;; so the final stepover needs to account for half the tool width (in addition to the diameter)
  ;; hence the hack below. TODO: make goodlier.
  (let [stepovers (stepovers (:tool-width config) stepover-amount (+ (float (/ (+ (:tool-width config) diameter) 2))))]
    (mapcat (fn [y-step radius]
              (into [(gcode/relative-move config 0 y-step 0)]
                    (arc-pocket config radius z-end)))
            stepovers
            (radius-scale stepovers))))
