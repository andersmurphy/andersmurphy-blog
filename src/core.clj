(ns core
  (:require [hiccup.core :refer [html]]
            [clojure.java.io :refer [make-parents]]
            [markdown.core :refer [md-to-html-string-with-meta]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.time LocalDateTime]))

(def site-url "https://andersmurphy.com")
(def site-title "Anders Murphy")
(def site-tagline "A blog mostly about software development")
(def site-github "https://github.com/andersmurphy")
(def site-twitter "https://twitter.com/anders_murphy")
(def site-linkedin "https://uk.linkedin.com/in/anders-murphy-76457b3a")
(def highlight-url "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0")
(def directory (io/file "resources/posts"))
(def files (sort (drop 1 (file-seq directory))))

(defn replace-n [s n match replacement]
  (if (= n 0)
    s
    (recur (str/replace-first s match replacement)
           (dec n)
           match
           replacement)))

(defn add-file [file]
  {:file file})

(defn add-file-name [{:keys [file] :as m}]
  (assoc m :file-name (.getName file)))

(defn add-path-name [{:keys [file-name] :as m}]
  (assoc m :path-name (str (-> (str/split file-name #".md")
                               first
                               (replace-n 3 #"-" "/"))
                           ".html")))

(defn add-date [{:keys [file-name] :as m}]
  (assoc m :date (->> (str/split file-name #"-")
                      (take 3))))

(def month-int->month-str
  {"01" "Jan" "02" "Feb" "03" "Mar" "04" "Apr" "05" "May" "06" "Jun"
   "07" "Jul" "08" "Aug" "09" "Sep" "10" "Oct" "11" "Nov" "12" "Dec"})

(defn date->post-date [[year month day]]
  (str day " " (month-int->month-str month) " " year))

(defn date->datetime [[year month day]]
  (str (str/join "-" [year month day]) "T00:00:00+00:00"))

(defn current-year []
  (-> (LocalDateTime/now) str (str/split #"-") first))

(defn first-paragraph [s]
  (-> (str/split s #"<p>")
      second
      (str/split #"</p>")
      first))

(defn head [title]
  [:head
   [:meta {:charset "UTF-8"}]
   [:title title]
   ;; styles
   [:link {:rel  "stylesheet"
           :type "text/css"
           :href (str site-url "/styles.css")}]
   [:link {:rel  "stylesheet"
           :type "text/css"
           :href (str site-url "/theme.css")}]
   ;; code highlights
   [:link {:rel  "stylesheet"
           :href (str site-url "/nord.css")}]
   [:script {:src (str highlight-url "/highlight.min.js")}]
   [:script {:src (str highlight-url "/languages/clojure.min.js")}]
   [:script "hljs.initHighlightingOnLoad();"]
   ;; icons
   [:link {:rel   "apple-touch-icon-precomposed"
           :sizes "144x144"
           :href  (str site-url "/assets/apple-touch-icon-precomposed.png")}]
   [:link {:rel  "shortcut icon"
           :href (str site-url "/assets/favicon.ico")}]
   ;; enables responsiveness on mobile devices
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1.0"}]
   ;; google description
   [:meta {:name    "description"
           :content "A blog mostly about software development."}]])

(def sidebar
  [:div {:class "sidebar"}
   [:div {:class "container sidebar-sticky"}
    [:div {:class "sidebar-about"}
     [:img {:class "portrait"
            :src   (str site-url "/assets/anderspixel.png")}]
     [:h1 [:a {:href site-url}
           site-title]]
     [:p {:class "lead"}
      site-tagline]]
    [:nav {:class "sidebar-nav"}
     [:a {:class "sidebar-nav-item" :href site-github} "Github"]
     [:a {:class "sidebar-nav-item" :href site-twitter} "Twitter"]
     [:a {:class "sidebar-nav-item" :href site-linkedin} "LinkedIn"]]
    [:p (str "@ " (current-year) ". All rights reserved")]]])

(defn add-content [{:keys [file] :as m}]
  (let [{:keys [html metadata]} (-> file slurp (md-to-html-string-with-meta))]
    (assoc m :content html
           :post-name (-> metadata :title first))))

(defn prepend-doctype-header [html]
  (str "<!DOCTYPE html>\n" html))

(defn add-page [{:keys [post-name date content date] :as m}]
  (->> (html [:html
              (head post-name)
              [:body
               sidebar
               [:div {:class "content container"}
                [:article {:class "post"}
                 [:h1 {:class "post-title"} post-name]
                 [:time {:class    "post-date"
                         :datetime (date->datetime date)}
                  (date->post-date date)]
                 content]]]])
       prepend-doctype-header
       (assoc m :page)))

(defn page-html [{:keys [page-content previous-page-url next-page-url] :as m}]
  (->> (html [:html
              (head site-title)
              [:body
               sidebar
               [:div {:class "content container"}
                [:div {:class "posts"}
                 (map (fn [{:keys [post-name date path-name content]}]
                        [:article {:class "post"}
                         [:h1 {:class "post-title"}
                          [:a {:href (str site-url "/" path-name)} post-name]]
                         [:time {:class    "post-date"
                                 :datetime (date->datetime date)}
                          (date->post-date date)]
                         [:p (first-paragraph content)]])
                      page-content)]
                (when previous-page-url
                  [:a {:href (str site-url "/" previous-page-url)}
                   "Newer Posts"])
                (when next-page-url
                  [:a {:href (str site-url "/" next-page-url)}
                   "Older Posts"])]]])
       prepend-doctype-header
       (assoc m :page-content)))

(def html-404
  (->> (html [:html
              (head site-title)
              [:body
               sidebar
               [:div {:class "content container"}
                [:article {:class "post"}
                 [:h1 {:class "post-title"} "404: Page not found"]
                 [:p "Sorry, we've misplaced that URL or it's
                 pointing to something that doesn't exist."
                  [:a {:href site-url} "Head back home"]
                  " to try finding it again."]]]]])
       prepend-doctype-header))

(defn write-post! [{:keys [page path-name]}]
  (let [docs-path-name (str "docs/" path-name)]
    (make-parents docs-path-name)
    (spit docs-path-name page)))

(defn link-pages [pages]
  (reduce (fn [pages next-page]
            (let [previous-page (first pages)]
              (if previous-page
                (conj (drop 1 pages)
                      (assoc previous-page
                             :next-page-url
                             (:page-path-name next-page))
                      (assoc next-page
                             :previous-page-url
                             (:page-path-name previous-page)))
                (conj pages next-page))))
          '() pages))

(defn add-page-urls [pages]
  (let [number-of-pages (count pages)]
    (->> (inc number-of-pages)
         (range 2)
         (map #(str "docs/page/" % ".html"))
         (into ["docs/index.html"])
         (map (fn [page url] {:page-content   page
                              :page-path-name url})
              pages)
         link-pages)))

(defn write-page! [{:keys [page-content page-path-name]}]
  (make-parents page-path-name)
  (spit page-path-name page-content))

(defn write-404! [s]
  (let [path-name "docs/404.html"]
    (spit path-name s)))

(defn get-posts [files]
  (-> (sequence
       (comp (map add-file)
             (map add-file-name)
             (map add-path-name)
             (map add-date)
             (map add-content)
             (map add-page))
       files)
      reverse))

(defn generate-site []
  (let [posts (get-posts files)]
    (->> (partition-all 10 posts)
         add-page-urls
         (map page-html)
         (run! write-page!))
    (-> html-404
        write-404!)
    (run! write-post! posts)))
