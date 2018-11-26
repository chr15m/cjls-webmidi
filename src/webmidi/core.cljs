(ns webmidi.core
    (:require
      [reagent.core :as r]))

(def midiaccess (aget js/navigator "requestMIDIAccess"))

(defn get-inputs [raw-inputs]
  (loop [input (.next raw-inputs) found #{}]
    (let [device (.. input -value)]
      (if (and input (not (.. input -done)))
        (recur (.next raw-inputs) (conj found device))
        found))))

(defn parse-first-byte [b]
  (let [lnib (-> b (bit-and 0xf0) (bit-shift-right 4))
        rnib (bit-and b 0x0f)]
    [(case lnib
       8 :note-off
       9 :note-on
       11 :controller
       14 :pitch-bend
       15 :after-touch
       lnib) rnib]))

(defn on-midi-message [device midistate message]
  ;(js/console.log (.-name device) message)
  (let [data (vec (js/Array.from (.-data message)))
        [path value] (if (= (bit-and (first data) 0xf0) 0xf0)
                       ; sysex message
                       (let [kind (bit-and (first data) 0x0f)]
                         [[(.-name device) kind] (rest data)])
                       ; note/controller message
                       (let [[kind channel] (parse-first-byte (first data))
                             k (second data)
                             v (last data)]
                         ; (print kind channel note volume)
                         (if (contains? #{:note-on :note-off} kind)
                           [[(.-name device) channel :note k] (case kind :note-on v :note-off 0)]
                           [[(.-name device) channel kind k] v])))]
    (swap! (midistate :raw) assoc-in path value)))

(defn checker [midistate]
  (let [midistate (or midistate {})
        midi (midistate :midi)
        raw-inputs (.values (.. midi -inputs))
        devices (midistate :devices)
        found (get-inputs raw-inputs)
        added (clojure.set/difference found devices)
        removed (clojure.set/difference devices found)
        midistate (assoc midistate :devices found)]
    ; TODO: add/remove events / channel puts
    (when (not-empty added)
      (doseq [device added]
        (swap! (midistate :raw) assoc (aget device "name") {})
        (aset device "onmidimessage" (partial #'on-midi-message device midistate))))
    (when (not-empty removed)
      (doseq [device removed]
        (swap! (midistate :raw) dissoc (aget device "name")))
      (print "removed" removed))
    (js/setTimeout
      (partial #'checker midistate)
      500)))

(defn init [& [raw-atom callback]]
  (let [midi-state {:listeners (atom {}) :raw ((or raw-atom atom) {})}
        p (.call midiaccess js/navigator #js {:sysex true})]
    (.then p
           (fn [midi]
             (let [updated-midi-state (assoc midi-state :midi midi :devices #{})]
               (checker updated-midi-state)
               (when callback (callback updated-midi-state))))
           (fn [err]
             (when callback (callback {:error err}))))
    midi-state))

;; -------------------------
;; Views

(defn home-page [raw-midi-data]
  [:div [:h1 "webmidi tester"]
   ;[:pre (str @raw-midi-data)]
   (doall
     (for [[n d] @raw-midi-data]
       [:div {:key n}
        [:h2 n]
        ;[:pre (str d)]
        (for [[channel-id channel] d]
          [:div
           [:h3 "Channel " channel-id]
           (for [[controller value] (channel :controller)]
             ;[:p controller "->" value]
             [:svg {:viewBox "0 0 120 120" :width 120 :height 120}
              [:circle {:cx 60 :cy 60 :r 50 :stroke "#eee" :fill "none" :stroke-width "10px" :stroke-dasharray (* 2 js/Math.PI 50) :stroke-dashoffset (* 2 js/Math.PI 50 0.25) :transform "rotate(-225 60 60)"}]
              [:circle {:cx 60 :cy 60 :r 50 :stroke "#8af" :fill "none" :stroke-width "10px" :stroke-dasharray (* 2 js/Math.PI 50) :stroke-dashoffset (* 2 js/Math.PI 50 (- 1 (* (/ value 127) 0.75))) :transform "rotate(-225 60 60)"}]])
           (let [notes (channel :note)]
             (when notes
               [:svg {:viewBox "0 0 1270 100"}
                (for [note (range 127)]
                  (let [on (> (get notes note) 0)]
                  [:rect {:x (* note 10) :y 0 :width 10 :height 100 :fill (if on "#8af" "#eee") :fill-opacity (if on (+ (* (/ (get notes note) 127) 0.9) 0.1) 1.0)}]))]))])]))])

;; -------------------------
;; Initialize app

(defn mount-root []
  (defonce midi (init r/atom))
  (r/render [home-page (midi :raw)] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
