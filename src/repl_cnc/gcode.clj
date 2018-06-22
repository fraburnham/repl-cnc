(ns repl-cnc.gcode
  (:require [clojure.string :as str]))

(defn build-gcode-fn-seq
  [gcode-map]
  (map
   (fn [[fn-keyword gcode]]
     `(def ~(symbol (name fn-keyword)) ~gcode))
   gcode-map))

(defmacro defgcodes
  "Define gcode fns based on map keyword"
  [gcode-map]
  `(do
    ~@(build-gcode-fn-seq gcode-map)))

(defgcodes {:relative-mode "G91"
            :absolute-mode "G90"
            :stop-spindle "M5"
            :local-home "G92 X0 Y0 Z0"
            :clear-local-home "G92.1"
            :start-ccw-spindle "M4"
            :start-cw-spindle "M3"
            :spindle-speed "S"
            :feedrate "F"
            :line "G1"
            :arc-line "G2"
            :inch-units "G20"
            :mm-units "G21"})

(defn start-spindle
  ([config]
   (start-spindle config :cw))
  ([config direction]
   (if (= direction :ccw)
     (str/join " " [start-ccw-spindle spindle-speed (:rpm config)])
     (str/join " " [start-cw-spindle spindle-speed (:rpm config)]))))

(defn relative-move
  ([config x y z]
   ;; do the math needed to account for tool width
   ;; stop points will be the _outer bounds_ of the move (outside of the tool, not center)
   (relative-move config x y z (:feedrate config)))
  ([config x y z feedrate-value]
   (str/join " "
             [relative-mode
              line
              "X" x
              "Y" y
              "Z" z
              feedrate feedrate-value])))

(defn absolute-move
  "Specify `nil` to not move on an axis"
  [config x y z]
  (str/join " "
            [absolute-mode
             line
             (if x (str "X" x))
             (if y (str "Y" y))
             (if z (str "Z" z))
             feedrate (:feedrate config)]))

;; config probably shouldn't make it this far.
;; let the controller strip it away
(defn plunge
  ([config]
   (plunge config (:plunge-depth config)))
  ([config distance]
   ;; assert (via spec?) that the tool-config is valid enough
   (relative-move config 0 0 distance (:plunge-feedrate config))))

;; most of these shouldn't set relative motion explicitly!
(defn arc
  [config x-end y-end x-offset y-offset]
  (str/join " "
            [relative-mode
             arc-line
             "X" x-end
             "Y" y-end
             "I" x-offset
             "J" y-offset
             feedrate (:feedrate config)]))

;; maybe add stepover gcode that can handle tool width in here
;; and plunge gcode fn, too
;; then the primitives just pass the tool info through
