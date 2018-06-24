(ns repl-cnc.shapes.ring
  (:require [repl-cnc.shapes.primitives :as primitives]
            [repl-cnc.gcode :as gcode]
            [repl-cnc.tools :as tools]))

(defn ring-pocket
  "Make a ring shaped pocket"
  [config inner-diameter width z-end stepover-amount]
  (let [stepovers (primitives/stepovers (:tool-width config) stepover-amount width)
        radii (primitives/radius-scale stepovers
                                       (float ; tool's outside edge is on the inside edge of the pocket
                                        (+ (/ inner-diameter 2)
                                           (/ (:tool-width config) 2))))]
    (reduce
     (fn [steps [y-step radius]]
       (-> (conj steps (gcode/relative-move config 0 y-step 0))
           (into (primitives/arc-pocket config radius z-end))))
     []
     (->> (interleave (into [0] stepovers) radii)
          (partition 2)))))

(defn surface
  "Mill the z surface of the ring and cleanup edges"
  [config inner-diameter width height z-end stepover-amount]
  (-> (into [(gcode/relative-move config
                                   0
                                   (tools/working-tool-width config stepover-amount)
                                   0)]
            (ring-pocket config
                         (- inner-diameter (* 2 (tools/working-tool-width config stepover-amount)))
                         (+ width (:tool-width config))
                         (+ height z-end)
                         stepover-amount))
      (conj (gcode/relative-move config
                                 0
                                 (- (:tool-width config) (tools/working-tool-width config stepover-amount))
                                 0))))

(defn cutout-pocket
  "Create a pocket around the ring for removal" ; TODO this is where tabs and full cut through should be used
  [config inner-diameter width z-end stepover-amount]
  (ring-pocket config
               (+ inner-diameter (* width 2))
               (float (+ (:tool-width config) (tools/working-tool-width config stepover-amount)))
               z-end
               stepover-amount))

(defn ring
  "Tabs aren't added yet. Do not cut through the bottom of the stock"
  [config inner-diameter width height z-end stepover-amount]
  (-> (primitives/circle-pocket config inner-diameter z-end stepover-amount) ; inner hole
      (into (surface config inner-diameter width height z-end stepover-amount))
      (into (cutout-pocket config inner-diameter width z-end stepover-amount))))
