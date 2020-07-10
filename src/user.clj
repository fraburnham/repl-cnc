(ns user
  (:require [com.stuartsierra.component :as component]
            [repl-cnc
             [core :as repl-cnc]
             [gcode :as gcode]
             [tools :as tools]]
            [repl-cnc.shapes
             [primitives :as shapes]
             [ring :as ring]]
            [repl-cnc.serial.proto :as serial]
            [repl-cnc.file :as file]))

(defonce system nil)

(defn start-system [port-name]
  ;; this feels wrong somehow
  (alter-var-root #'system (fn [&_] (component/start (repl-cnc/cnc-system port-name)))))

(defn stop-system []
  (alter-var-root #'system component/stop))

(def hardwood
  {:feedrate 8
   :plunge-feedrate 1
   :plunge-depth -0.5})

(def foam
  {:feedrate 100
   :plunge-feedrate 50
   :plunge-depth -1})

(def end-mill
  {:tool-width 3})

(def default-spindle
  {:rpm 12000})

(def config
  (merge
   hardwood
   end-mill
   default-spindle))

(repl-cnc.serial.port/list-ports)
