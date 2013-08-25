(ns lt.objs.menu
  (:require [lt.object :as object]
            [lt.objs.command :as cmd]
            [lt.objs.platform :as platform]
            [lt.objs.window :as window]
            [lt.util.dom :as dom]))

(def gui (js/require "nw.gui"))

(defn create-menu [type]
  (let [m (.-Menu gui)]
    (if type
      (m. (js-obj "type" type))
      (m.))))

(def menu-instance (create-menu))

(defn submenu [items]
  (let [menu (create-menu)]
    (doseq [i items
            :when i]
      (.append menu (menu-item i)))
    menu))

(defn menu-item [opts]
  (let [mi (.-MenuItem gui)
        opts (if-not (:submenu opts)
               opts
               (assoc opts :submenu (submenu (:submenu opts))))]
    (mi. (clj->js opts))))

(defn clear! [menu]
  (dotimes [i (.-items.length (or menu menu-instance))]
    (.removeAt (or menu menu-instance) 0)))

(defn menu [items]
  (clear!)
  (doseq [i items]
    (.append menu-instance (menu-item i)))
  menu-instance)

(defn zoom-adjust [zoom v]
  (cond
   (= 0 zoom) v
   (> zoom 0) (int (* v (+ 1 (* zoom 0.20))))
   (< zoom 0) (int (* v (/ -1 (+ -1 (* zoom 0.27)))))))

(defn show-menu [m x y]
  (let [[x y] (if-let [zoom (window/zoom-level)]
                [(zoom-adjust zoom x) (zoom-adjust zoom y)]
                [x y])]
    (.popup m x y)))

(dom/on (dom/$ :body) :contextmenu (fn [e]
                                     (dom/prevent e)
                                     (dom/stop-propagation e)
                                     false))

(def menubar (create-menu "menubar"))

(defn set-menubar [items]
  (clear! menubar)
  (doseq [i items
          :when i]
    (.append menubar (menu-item i)))
  (set! (.-menu window/me) menubar))


(set-menubar [
              (when (platform/mac?)
                {:label "" :submenu [{:label "About Light Table" :click (fn [] (cmd/exec! :version))}
                                     {:type "separator"}
                                     {:label "Hide Light Table" :key "h" :selector "hide:"}
                                     {:label "Hide Others" :key "h" :modifiers "cmd-alt" :selector "hideOtherApplications:"}
                                     {:type "separator"}
                                     {:label "Quit Light Table" :key "q" :selector "closeAllWindows:"}]})
              {:label "File" :submenu [{:label "New file" :key "n" :click #(cmd/exec! :new-file)}
                                       {:label "Open file" :key "o" :click #(cmd/exec! :open-file) :modifiers "cmd-shift"}
                                       {:label "Open folder" :click #(do
                                                                       (cmd/exec! :workspace.show :force)
                                                                       (cmd/exec! :workspace.add-folder))}
                                       {:label "Save file" :key "s" :click #(cmd/exec! :save)}
                                       {:label "Save file as.." :key "s" :click #(cmd/exec! :save-as) :modifiers "cmd-shift"}
                                       {:type "separator"}
                                       {:label "New window" :key "n" :modifiers "cmd-shift" :click #(cmd/exec! :window.new)}
                                       {:label "Close window" :key "w" :modifiers "cmd-shift" :click #(cmd/exec! :window.close)}
                                       {:type "separator"}
                                       {:label "Close file" :key "w" :click #(cmd/exec! :tabs.close)}
                                       ]}
              (if (platform/mac?)
                {:label "Edit" :submenu [{:label "Undo" :selector "undo:" :key "z"}
                                         {:label "Redo" :selector "redo:" :key "z" :modifiers "cmd-shift"}
                                         {:type "separator"}
                                         {:label "Cut" :selector "cut:" :key "x"}
                                         {:label "Copy" :selector "copy:" :key "c"}
                                         {:label "Paste" :selector "paste:" :key "v"}
                                         {:label "Select All" :selector "selectAll:" :key "a"}
                                         ]}
                {:label "Edit" :submenu [{:label "Undo" :click #(cmd/exec! :editor.undo)}
                                         {:label "Redo" :click #(cmd/exec! :editor.redo)}
                                         {:type "separator"}
                                         {:label "Cut" :click #(cmd/exec! :editor.cut) }
                                         {:label "Copy" :click #(cmd/exec! :editor.copy)}
                                         {:label "Paste" :click #(cmd/exec! :editor.paste)}
                                         {:label "Select All" :click #(cmd/exec! :editor.select-all)}
                                         ]}
                )
              {:label "View" :submenu [{:label "Workspace" :key "t" :click (fn [] (cmd/exec! :workspace.show))}
                                       {:label "Connections" :click #(cmd/exec! :show-connect)}
                                       {:label "Navigator" :key "o" :click #(cmd/exec! :navigate-workspace-transient)}
                                       {:label "Commands" :key " " :modifiers "ctrl" :click #(cmd/exec! :show-commandbar-transient)}
                                       {:type "separator"}
                                       {:label "Console" :click #(cmd/exec! :toggle-console)}
                                       ]}

              {:label "Window" :submenu [{:label "Minimize" :click #(cmd/exec! :window.minimize)}
                                         {:label "Maximize" :click #(cmd/exec! :window.maximize)}
                                         {:label "Fullscreen" :click #(cmd/exec! :window.fullscreen)}]}
              {:label "Help" :submenu [{:label "Documentation" :click (fn [] (cmd/exec! :show-docs))}
                                       (when-not (platform/mac?)
                                         {:label "About Light Table" :click #(cmd/exec! :version)})]}
              ]
             )

(object/behavior* ::set-menu
                  :triggers #{:focus :init}
                  :reaction (fn [this]
                              (when (or (platform/mac?)
                                        (not (.-menu window/me)))
                                (set! (.-menu window/me) menubar))))

(object/behavior* ::remove-menu-close
                  :triggers #{:closed :blur}
                  :reaction (fn [this]
                              (when (platform/mac?)
                                (set! (.-menu window/me) nil))))

(object/behavior* ::menu!
                  :triggers #{:menu!}
                  :reaction (fn [this e]
                              (let [items (sort-by :order (filter identity (object/raise-reduce this :menu+ [])))]
                                (-> (menu items)
                                    (show-menu (.-clientX e) (.-clientY e))))
                              (dom/prevent e)
                              (dom/stop-propagation e)))
