(ns repl-cnc.core
  (:require [com.stuartsierra.component :as component]
            [repl-cnc.serial
             [controller :as cnc-controller]
             [port :as cnc-port]]))

;; make a system here

(defn cnc-system [port-name]
  (component/system-map
   :cnc-port (cnc-port/map->CNCPort {:port (cnc-port/get-port port-name)
                                     :mode-flags [:read-semi-blocking :write-semi-blocking]
                                     :read-timeout 1000 ; make conf-able
                                     :write-timeout 1000
                                     :baud-rate (:grbl cnc-port/baud-rates)})
   :cnc-controller (component/using (cnc-controller/map->CNCController {})
                                    [:cnc-port])))

;; now that the sends are blocking put them in a thread or go block
;; maybe a thread, I'm not sure I want it to park...
;; of course this means creating a system and a way to kill the child
