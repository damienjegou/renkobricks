(ns renkobricks.core
  (:require [taoensso.carmine :as car]
            [clojure.stacktrace :as st])
  (:import (java.util Date)
           (java.text SimpleDateFormat))
  (:gen-class))

;; redis
(def pool         (car/make-conn-pool))
(def spec-server1 (car/make-conn-spec))

(defmacro wcar [& body] `(car/with-conn pool spec-server1 ~@body))

(def bricksize (ref 100))
(def lastbrick (ref nil))

;; utility functions

(defn abs [x]
  (if (< x 0)
    (- x)
    x))

(defn log [& args]
  (spit "traderbot.log"
        (str (.format (new SimpleDateFormat "yyyy/MM/dd HH:mm:ss") (new Date)) ; prepend date
             " "
             (apply format args)
             "\n")
        :append true))


(defn makebrick [rawmsg]
  (try
    (let [msg (apply hash-map (read-string (last rawmsg)))
          bid (:valuationBidPrice msg)
          ask (:valuationAskPrice msg)
          avg (/ (+ bid ask) 2)]
      (log "%s %s %s" @lastbrick avg (if @lastbrick (abs (- @lastbrick avg))
                                         "nil"))
      (if (not @lastbrick)
        (do (dosync (ref-set lastbrick (* (quot avg 100) 100)))
            (wcar (car/publish "Bricks1" (str @lastbrick))) (log "test1")
            (log "First brick %s" (str @lastbrick)))
        (if (> (abs (- @lastbrick avg)) 100)
          (do (dosync (ref-set lastbrick (* (quot avg 100) 100)))
              (wcar (car/publish "Bricks1" (str @lastbrick))) (log "test2")
              (log "New brick %s" (str @lastbrick))))))
    (catch Exception e
      (log "exception %s" e))))

(defn -main [& args]
  (let [listen-orders (car/with-new-pubsub-listener
                        spec-server1 {"OrderBookEvent" makebrick}
                        (car/subscribe "OrderBookEvent"))]
    (log "Start making bricks...")
    ;; useful ?
    (while true
      (Thread/sleep 500))))
  ;; (let [listener (car/with-new-pubsub-listener
  ;;                  spec-server1 {"OrderBookEvent" (fn [rawmsg]
  ;;                                                   ;(try (if (= (first rawmsg) "message")))
  ;;                                                          (let [msg (apply hash-map (read-string (last rawmsg)))
  ;;                                                                bid (:valuationBidPrice msg)
  ;;                                                                ask (:valuationAskPrice msg)
  ;;                                                                avg (/ (+ bid ask) 2)]
  ;;                                                            ;(log "bid : %s, ask : %s, avg : %s" bid ask avg)
  ;;                                                            (if (not @lastbrick)
  ;;                                                              (do (dosync (ref-set lastbrick (* (quot avg 100) 100)))
  ;;                                                                  (wcar (car/publish "Bricks1" @lastbrick))
  ;;                                                                  (log "First brick"))
  ;;                                                              (if (> (abs (- @lastbrick avg)) 100)
  ;;                                                                (do (dosync (ref-set lastbrick (* (quot avg 100) 100)))
  ;;                                                                    (wcar (car/publish "Bricks1" @lastbrick))
  ;;                                                                    (log "New brick"))))))}
  ;;                                                    ;    (catch Exception e (log "%s" e))
  ;;                  (car/subscribe "OrderBookEvent"))]
  ;;   (while true (Thread/sleep 500)))
