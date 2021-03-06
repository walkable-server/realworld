(ns conduit.handler.article
  (:require [ataraxy.core :as ataraxy]
            [buddy.sign.jwt :as jwt]
            [conduit.common :as common]
            [conduit.boundary.article :as article]
            [ataraxy.response :as response]
            [integrant.core :as ig]))

(defmethod ig/init-key ::create [_ {:keys [db]}]
  (fn [{[_ article] :ataraxy/result
        id          :identity}]
    (when id
      (let [article-id (article/create-article db
                         (assoc article :author_id (:user-id id)))]
        [::response/created (str "/artiles/" article-id)]))))

(defmethod ig/init-key ::destroy [_ {:keys [db]}]
  (fn [{[_ article-slug] :ataraxy/result
        id          :identity}]
    (when id
      (let [article-id (article/destroy-article db (:user-id id) article-slug)]
        [::response/ok "deleted"]))))

(defmethod ig/init-key ::like [_ {:keys [db]}]
  (fn [{[_ article-slug] :ataraxy/result
        id          :identity}]
    (when id
      (let [article-id (article/like db (:user-id id) article-slug)]
        [::response/ok "liked"]))))

(defmethod ig/init-key ::unlike [_ {:keys [db]}]
  (fn [{[_ article-slug] :ataraxy/result
        id          :identity}]
    (when id
      (let [article-id (article/unlike db (:user-id id) article-slug)]
        [::response/ok "unliked"]))))

(defmethod ig/init-key ::create-comment [_ {:keys [db]}]
  (fn [{[_ article-slug comment-item] :ataraxy/result
        id          :identity}]
    (when id
      (let [comment-id (article/create-comment db article-slug
                         (assoc comment-item :author_id (:user-id id)))]
        [::response/created "comment"]))))

(defmethod ig/init-key ::destroy-comment [_ {:keys [db]}]
  (fn [{[_ comment-id] :ataraxy/result
        id          :identity}]
    (when id
      (let [article-id (article/destroy-comment db (:user-id id) comment-id)]
        [::response/ok "deleted"]))))

(defn parse-int [s]
  (when-let [d (re-find  #"\d+" s )]
    (Integer. d)))

(defn handle-articles
  [feed? resolver]
  (fn [{[_ {tag    "tag"    author "author" liked-by "favorited"
            offset "offset" limit  "limit"}] :ataraxy/result
        id                                   :identity}]
    (let [top-query (if (= :feed feed?)
                      :articles/feed
                      :articles/all)]
      [::response/ok
       (-> (resolver (:user-id id)
             [{(list top-query
                 {:order-by [:article/created-at]
                  :offset   (when (string? offset) (parse-int offset))
                  :limit    (when (string? limit) (parse-int limit))
                  :filters  (merge
                              (when (and tag (string? tag) (seq tag))
                                {:article/tags [:in :tag/tag tag]})
                              (when (and author (string? author) (seq author))
                                {:article/author [:in :user/username author]})
                              (when (and liked-by (string? liked-by) (seq liked-by))
                                {:article/liked-by [:in :user/username liked-by]}))})
               common/article-query}])
         common/with-articles-count
         common/clj->json)])))

(defmethod ig/init-key ::all-articles
  [_ {:keys [resolver]}]
  (handle-articles :not-feed resolver))

(defmethod ig/init-key ::feed
  [_ {:keys [resolver]}]
  (handle-articles :feed resolver))

(defmethod ig/init-key ::by-slug
  [_ {:keys [resolver]}]
  (fn [{[_ slug] :ataraxy/result
        id       :identity}]
    (let [ident-key [:article/by-slug slug]
          result (-> (resolver (:user-id id) [{ident-key [{:placeholder/article common/article-query}]}])
                   (get ident-key))]
      (if (seq result)
        [::response/ok (common/clj->json result)]
        [::response/not-found]))))

(defmethod ig/init-key ::comments-by-slug
  [_ {:keys [resolver]}]
  (fn [{[_ slug] :ataraxy/result
        id       :identity}]
    (let [ident-key [:article/by-slug slug]
          result (-> (resolver (:user-id id) [{ident-key common/comment-query}])
                   (get ident-key))]
      (if (seq result)
        [::response/ok (common/clj->json result)]
        [::response/not-found]))))
