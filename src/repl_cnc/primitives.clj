(ns repl-cnc.primitives
  (:require [repl-cnc.gcode :as gcode]))

(defn with-spindle
  [config steps]
  (as-> [(gcode/start-spindle config)] *
    (apply conj * steps)
    (conj * gcode/stop-spindle)))

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

(defn slot
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

(defn box-hole
  "Recursively build the box hole steps assuming bounds are max limits for the outside of the tool"
  [config x-end y-end z-end stepover-amount]
  (-> (reduce (fn [steps y-step]
                (-> (conj steps (gcode/relative-move config 0 y-step 0))
                    (into (slot config x-end 0 z-end))))
              (into [gcode/local-home] (slot config x-end 0 z-end))
              (stepovers (:tool-width config) stepover-amount y-end))))

(defn circle-slot
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
  [stepovers]
  (->> (reduce
        (fn [radii stepover-distance]
          (conj radii (+
                       (last radii)
                       stepover-distance)))
        [0]
        stepovers)
       (map -)))

(defn circle-hole
  "The start of the hole is centroid"
  [config radius z-end stepover-amount]
  ;; use half of tool width since we're working w/ radius stepovers (and double stepover since it is a pct of total tool)
  (let [stepovers (stepovers (float (/ (:tool-width config) 2)) (* stepover-amount 2) (float (/ radius 2)))
        radii (radius-scale stepovers)]
    (reduce (fn [steps [y-step radius]]
              (-> (conj steps (gcode/relative-move config 0 y-step 0))
                  (into (circle-slot config radius z-end))))
            []
            (->> (interleave (into [0] stepovers) radii)
                 (partition 2)))))
