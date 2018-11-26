(ns webmidi.prod
  (:require
    [webmidi.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
