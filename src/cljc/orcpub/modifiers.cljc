(ns orcpub.modifiers
  (:require [clojure.spec.alpha :as spec]
            [orcpub.entity-spec :as es]))

(def cumulative-numeric-modifier-type ::cumulative-numeric)
(def override-modifier-type ::overriding)
(def cumulative-list-modifier-type ::cumulative-list)

(spec/def ::value any?)
(spec/def ::path-token (spec/or :key keyword?
                                :int int?))
(spec/def ::path-tokens (spec/+ ::path-token))
(spec/def ::path (spec/or :key ::path-token
                          :full-path ::path-tokens))
(spec/def ::type #{cumulative-numeric-modifier-type
                   override-modifier-type
                   cumulative-list-modifier-type})
(spec/def ::modifier (spec/keys :opt [::name ::value ::fn ::key ::deps ::deferred-fn ::default-value ::val-fn]))
(spec/def ::modifiers (spec/+ ::modifier))
(spec/def ::keywords (spec/+ keyword?))

(spec/def ::key keyword?)
(spec/def ::args sequential?)
(spec/def ::mod-cfg (spec/keys :req [::key]
                               :opt [::args]))

(defn bonus-str [bonus]
  (if (pos? bonus)
    (str "+" bonus)
    (str bonus)))

(defn mod-f [nm value fn k deps & [conditions order-number]]
  {::name nm
   ::value value
   ::fn fn
   ::key k
   ::deps deps
   ::conditions conditions
   ::order order-number})

(defn deferred-mod [nm deferred-fn k default-value val-fn deps]
  {::name nm
   ::key k
   ::deferred-fn deferred-fn
   ::default-value default-value
   ::val-fn val-fn
   ::deps deps})

(defmacro modifier [prop body & [nm value conditions order-number]]
  (let [full-body (if conditions [conditions body] body)]
    `(mod-f ~nm
            ~value
            (es/modifier ~prop ~body)
            (es/ref-sym-to-kw '~prop)
            (es/dependencies ~prop ~full-body)
            (es/conditions ~conditions)
            ~order-number)))

(defmacro deferred-modifier [prop deferred-fn default-value & [nm val-fn]]
  `(deferred-mod ~nm ~deferred-fn (es/ref-sym-to-kw '~prop) ~default-value ~val-fn (es/dependencies ~prop deferred-fn)))

(defmacro cum-sum-mod [prop bonus & [nm value conditions]]
  (let [full-body (if conditions [conditions bonus] bonus)]
    `(mod-f ~nm
            ~value
            (es/cum-sum-mod ~prop ~bonus)
            (es/ref-sym-to-kw '~prop)
            (es/dependencies ~prop ~full-body)
            (es/conditions ~conditions))))

(defmacro vec-mod [prop val & [nm value conditions]]
  `(mod-f ~nm
          ~value
          (es/vec-mod ~prop ~val)
          (es/ref-sym-to-kw '~prop)
          (es/dependencies ~prop ~val)
          (es/conditions ~conditions)))

(defmacro set-mod [prop body & [nm value conditions]]
  (let [full-body (if conditions (conj conditions body) body)]
    `(mod-f ~nm
            ~value
            (es/set-mod ~prop ~body)
            (es/ref-sym-to-kw '~prop)
            (es/dependencies ~prop ~full-body)
            (es/conditions ~conditions))))

(defmacro map-mod [prop k v & [nm value]]
  `(mod-f ~nm ~value (es/map-mod ~prop ~k ~v) (es/ref-sym-to-kw '~prop) (es/dependencies ~prop ~v)))

(defmacro fn-mod [prop func]
  `(mod-f nil nil (es/modifier ~prop (fn [] ~func)) (es/ref-sym-to-kw '~prop) (es/dependencies ~prop ~func)))


(defn modifier-fn [{:keys [::value ::fn ::deferred-fn]}]
  (if (and deferred-fn value)
    (deferred-fn value)
    fn))

(defn apply-modifiers [entity modifiers]
  (reduce
   (fn [e {:keys [::key ::conditions] :as mod}]
     (if (nil? mod)
       #?(:clj (prn "MODIFIER IS NULL!!!!!!!!!!!!!!"))
       #?(:cljs (js/console.warn "MODIFIER IS NULL!!!!!!!!!!")))
     (let [passes-conds? (every? #(% e) conditions)
           level-increase? (= key :hit-point-level-increases)]
       (if (and mod passes-conds?)
         (let [mod-fn-result (modifier-fn mod)]
           (if (fn? mod-fn-result)
             (mod-fn-result e)
             (reduce
              (fn [e2 f]
                (f e2))
              e
              mod-fn-result)))
         e)))
   entity
   modifiers))

(defn raw-args [args]
  (map (fn [{:keys [::int-arg ::string-arg ::keyword-arg]}]
         (or int-arg string-arg keyword-arg))
       args))
