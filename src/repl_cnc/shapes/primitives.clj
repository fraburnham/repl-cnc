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
  (let [sign (if (pos? end) + -)
        end (Math/abs end)]
    (loop [stepovers []
           distance-remaining (- end tool-width)] ;; initial hole (should add the 0th stepover then...
      (if (zero? distance-remaining)
        stepovers
        (let [stepover (next-stepover tool-width stepover-amount distance-remaining)]
          (recur (conj stepovers
                       (sign stepover))
                 (- distance-remaining stepover)))))))

(defn end-offset
  "End accounting for tool width"
  [tool-width end]
  (if (zero? end)
    end
    (- end tool-width)))

;; add some consideration for mill style
;; for now just do single passes if the mill style is set (assume the caller knows the details)
;; this is to get around some shitty edges in ebony line-pockets
(defn line-pocket
  "Assumes you're starting from relative zero
   z-end and plunge-depth-mm will be negative"
  [config x-end y-end z-end]
  ;; protect against invalid pluge-depth rates
  (let [steps (Math/ceil (Math/abs (/ z-end (:plunge-depth config))))
        cut-direction-map {0 +
                           1 -}]
    (-> (reduce
         (fn [r i]
           (let [cut-direction-modifier (get cut-direction-map (mod i 2))]
             (conj r
                   (gcode/plunge config)
                   (gcode/relative-move config
                                        (cut-direction-modifier
                                         (end-offset (:tool-width config) x-end))
                                        (cut-direction-modifier
                                         (end-offset (:tool-width config) y-end))
                                        0))))
         []
         (range steps))
        (conj (gcode/plunge config (- z-end))))))

;; boxes only work w/ even depths!
;; Use local home after retraction?
(defn square-pocket
  "Recursively build the box hole steps assuming bounds are max limits for the outside of the tool"
  [config x-end y-end z-end stepover-amount]
  (-> (reduce (fn [steps y-step]
                (-> (conj steps (gcode/relative-move config 0 y-step 0))
                    (into (line-pocket config x-end 0 z-end))))
              ;; the local home here is unused, I should be able to use it after each line-pocket
              ;; If I set it before each line-pocket
              (into [gcode/local-home] (line-pocket config x-end 0 z-end))
              (stepovers (:tool-width config) stepover-amount y-end))))

(defn arc-pocket
  "Doesn't account for any home reference except bottom for now"
  [config radius z-end]
  ;; There is a gcode to change the plane for the arc! 2.5d repl carving
  (let [steps (Math/ceil (/ z-end (:plunge-depth config)))]
    (-> (reduce
         (fn [r i]
           (conj r
                 (gcode/plunge config)
                 (gcode/arc config 0 0 0 radius)))
         []
         (range steps))
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
         stepovers)
        (map #(- (+ offset %))))))

(defn circle-pocket
  "The start of the hole is centroid"
  [config diameter z-end stepover-amount]
  ;; use half of tool width since we're working w/ radius stepovers (and double stepover since it is a pct of total tool)
  ;; this math is stupid. Fix it (after tests are added)
  (let [stepovers (stepovers (float (/ (:tool-width config) 2)) (* stepover-amount 2) (float (/ diameter 2)))
        radii (radius-scale stepovers)]
    (reduce (fn [steps [y-step radius]]
              (-> (conj steps (gcode/relative-move config 0 y-step 0))
                  (into (arc-pocket config radius z-end))))
            []
            (->> (interleave (into [0] stepovers) radii)
                 (partition 2)))))
