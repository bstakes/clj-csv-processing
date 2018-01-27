(ns csv-processing.core
  (:gen-class)
  (:require [clojure-csv.core :as csv]
            [semantic-csv.core :as scsv]
            [clojure.string :as s]))

;; # CSV Utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; really just a convenience wrapper around semantic csv
;;

(defn csv-with-headers
  "Convenience function to quickly load a CSV file and override column headers uses
   this uses the not lazy form of the semantic csv processing library and ignores
   the first row (header row) of the given csv file"
  [filepath headers]
  (vec (rest (scsv/slurp-csv filepath :header headers))))


;; # Data Structure Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; The base data structure is a hash-map with the user id as the key
;; the value is another hash map with the account details and augmented
;; with the transactions
;;
;; {"149" {:user-id "149"
;;         :initial-amount "2893"
;;         :program "2"
;;         :transactions {"01" [{:date "2017-01-14"
;;                               :user-id "149"
;;                               :amount "357"}
;;                              ...
;;                             ]
;;                        "02" []
;;                       }}}
;;

(defn accounts-by-user-id
  "Take the starting data an turn it into a hash-map by :user-id"
  [accounts]
  (zipmap (map :user-id accounts) accounts))

(defn transaction-month
  "Parse the transaction date to get the month it occurred in"
  [tx]
  (second (s/split (:date tx) #"-")))

(defn vec-or-append
  "If the first argument is nil, create a new vector with the second argument
   otherwise append the second argument to the given vector"
  [xs x]
  (if (empty? xs) [x] (conj xs x)))

(defn add-transaction
  "Add a single transaction to the correct user in the correct month"
  [accounts tx]
  (update-in accounts [(:user-id tx) :transactions (transaction-month tx)] vec-or-append tx ))

(defn transactions-to-account
  "Add a list of transactions to the correct user's account
   Transactions are organized by the month in which they occurred"
  [accounts transactions]
  ;; reduce is the standard way to build data structures in clojure
  (reduce add-transaction accounts transactions))

(defn accounts-with-transactions
  "Take the starting point, the transactions files and create the base data set"
  [start & files]
  (reduce
   #(transactions-to-account %1 (csv-with-headers %2 [:date :user-id :amount]))
   (accounts-by-user-id (csv-with-headers start [:user-id :initial-amount :program]))
   files))

;; # Utility functions to walk the data structure
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tx-amount
  "Get the amount value from a transaction and convert it to
   a type that works with mathematical operations"
  [tx]
  ((comp scsv/->int :amount) tx))

(defn transform-by-month
  "Create a hash map, by month, with the transform function applied to the list of
   transactions for that month"
  [transform transactions]
  (into (hash-map)
        (for [[month txs] transactions] [month (transform txs)])))

(defn most-by-transform
  "Finds the greatest value, determined by the transform function
   and the given month"
  [transform month accounts]
  (->> (vals accounts)
       (map transform)
       (sort-by #(get-in % [month]))
       (last)
       (#(select-keys % [:user-id month]))))


;; # Deposit Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn total-deposits
  "Given a list of transactions, add the deposits"
  [txs]
  (reduce + 0 (filter pos? (map tx-amount txs))))

(defn deposits-by-month
  "Given an account, find the amount deposited, per month"
  [{:keys [user-id transactions] :as account}]
  (conj {:user-id user-id}
        (transform-by-month total-deposits transactions)))

(defn most-deposited
  "Given a month and a list of accounts, find the :user-id
   and the amount deposited in that month"
  [month accounts]
  (most-by-transform deposits-by-month month accounts))

;; # Transaction Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; really just giving semantic value to count
(def total-transactions count)

(defn transactions-by-month
  "Given an account, find the number of transactions, per month"
  [{:keys [user-id transactions] :as account}]
  (conj {:user-id user-id}
        (transform-by-month total-transactions transactions)))

(defn most-transactions
  "Given a month and a list of accounts, find the :user-id
   and the number of transactions in that month"
  [month accounts]
  (most-by-transform transactions-by-month month accounts))

(defn transaction-amount-by-month
  "Given an account, find the net amount in transactions
   by month"
  [transactions]
  (reduce
   #(conj %1
          {(first %2)
           (reduce + 0 (map tx-amount (second %2)))}) {} transactions))

;; # Balance Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-balance
  "Given the total transaction amount per month, find the
   balance at the end of each month"
  [balances [month amount]]
  (if (not (map? balances))
    {month (+ amount (scsv/->int balances))}
    (conj balances
          {month (+ (second (last balances)) amount)})))

(defn balance-by-month
  "Given an account, calculate the running balance
   across all available months"
  [{:keys [user-id transactions initial-amount] :as account}]
  (conj {:user-id user-id}
        (last (reductions build-balance initial-amount (transaction-amount-by-month transactions)))))

;; # Penalty functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn gte
  "returns a function that compares whether the given value is greater
   than or equal to the original given value"
  [x]
  (fn [y] (>= y x)))

(def program-1
  {:deposits (gte 300)
   :transactions (gte 5)
   :balance (gte 1200)
   :penalty 8})

(def program-2
  {:deposits (gte 800)
   :transactions (gte 1)
   :balance (gte 5000)
   :penalty 4})

;; ran out of time to clean this up :/
(defn fines
  "Given an account, find penalties per month"
  [account]
  (let [totals ((juxt deposits-by-month transactions-by-month balance-by-month) account)
        program (if (= "1" (:program account)) program-1 program-2)
        monthly (for [month (rest (keys (first totals)))]
                  [month (map #(get-in % [month]) totals)])]
    {:user-id (:user-id account)
     :fines (reduce + 0 (map (fn [[_ [ds ts bs]]]
                                    (if (some true? [((:deposits program) ds)
                                                     ((:transactions program) ts)
                                                     ((:balance program) bs)])
                                      0
                                      (:penalty program)))
                             monthly))}))

(defn account-fines
  "list all accounts who owe fines"
  [accounts]
  (filter #(pos? (get-in % [:fines])) (map fines (vals accounts))))

;; # Run the data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def all-accounts (accounts-with-transactions "resources/StartingData.csv" "resources/Jan.csv" "resources/Feb.csv" "resources/Mar.csv"))

(account-fines all-accounts)

(defn -main
  []
  (println "Most deposits")
  (println (most-deposited "01" all-accounts))
  (println (most-deposited "02" all-accounts))
  (println (most-deposited "03" all-accounts))

  (println "Most transactions")
  (println (most-transactions "01" all-accounts))
  (println (most-transactions "02" all-accounts))
  (println (most-transactions "03" all-accounts))

  (println "Fines")
  (println (account-fines all-accounts)))

