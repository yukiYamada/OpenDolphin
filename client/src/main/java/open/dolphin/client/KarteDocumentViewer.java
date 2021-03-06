package open.dolphin.client;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.*;
import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import open.dolphin.delegater.DocumentDelegater;
import open.dolphin.delegater.OrcaRestDelegater;
import open.dolphin.helper.DBTask;
import open.dolphin.hiro.PrescriptionMaker;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.PVTHealthInsuranceModel;
import open.dolphin.letter.KartePDFImpl2;
import open.dolphin.letter.KartePDFMaker;
import open.dolphin.project.Project;

/**
 * DocumentViewer
 *
 * @author Minagawa,Kazushi
 *
 */
public class KarteDocumentViewer extends AbstractChartDocument implements DocumentViewer {

    // Busy プロパティ名
    public static final String BUSY_PROP = "busyProp";
    
//s.oh^ 2013/03/28 入力行が一行の場合に文字が全部表示されない。
    private static final int KARTE_OFFSET_HEIGHT = 50;
//s.oh$
    
//s.oh^ 2013/01/29 過去カルテの修正操作(選択状態)
    public static final Color DEFAULT_BGCOLOR = new Color(214, 217, 223);
    public static final Color DEFAULT_FGCOLOR = new Color(0, 51, 153);
    public static final Color SELECTED_BGCOLOR = new Color(50, 50, 100);
    public static final Color SELECTED_FGCOLOR = new Color(230, 230, 230);
//s.oh$

    // このアプリケーションは文書履歴を複数選択することができる
    // このリストはそれに対応した KarteViewer(2号カルテ)を保持している
    // このリストの内容（KarteViewer)が一枚のパネルに並べて表示される
    private List<KarteViewer> karteList;

    // 上記パネル内でマウスで選択されているカルテ(karteViewer)
    // 前回処方を適用した新規カルテはこの選択されたカルテが元になる
    private KarteViewer selectedKarte; // 選択されている karteViewer

    // busy プリパティ
    private boolean busy;

    // 文書履を昇順で表示する場合に true
    private boolean ascending;

    // 文書の修正履歴を表示する場合に true
    private boolean showModified;

    // このクラスの状態マネージャ
    private StateMgr stateMgr;

    // 選択を解除されたカルテのリスト
    private ArrayList<KarteViewer> removed;
    private JPanel scrollerPanel;

    /**
     * DocumentViewerオブジェクトを生成する。
     */
    public KarteDocumentViewer() {
        super();
        String title = ClientContext.getMyBundle(KarteDocumentViewer.class).getString("title.document");
        setTitle(title);
    }

    /**
     * busy かどうかを返す。
     * @return busy の時 true
     */
    public boolean isBusy() {
        return busy;
    }

    @Override
    public void start() {
        karteList = new ArrayList<>(1);
        connect();
        stateMgr = new StateMgr();
        enter();
    }

    @Override
    public void stop() {
        if (karteList != null) {
            karteList.stream().forEach((karte) -> {
                karte.stop();
            });
            karteList.clear();
        }
    }

    @Override
    public void enter() {
        super.enter();
        stateMgr.enter();
    }

    /**
     * 選択されているKarteViwerを返す。
     * @return 選択されているKarteViwer
     */
    public KarteViewer getSelectedKarte() {
        return selectedKarte;
    }

    /**
     * マウスクリック(選択)されたKarteViwerをselectedKarteに設定する。
     * 他のカルテが選択されている場合はそれを解除する。
     * StateMgrを Haskarte State にする。
     * @param view 選択されたKarteViwer
     */
    public void setSelectedKarte(KarteViewer view) {

        KarteViewer old = selectedKarte;
        selectedKarte = view;
        //
        // 他のカルテが選択されている場合はそれを解除する
        //
        if (selectedKarte != old) {
            if (selectedKarte != null) {
                for (KarteViewer karte : karteList) {
                    karte.setSelected(false);
//s.oh^ 2013/01/29 過去カルテの修正操作(選択状態)
                    //if(karte.getUI() instanceof KartePanel2M) {
                    //    // アイコンの差し換え(デザイン変更)
                    //    ((KartePanel2M)karte.getUI()).getTimeStampPanel().setBackground(DEFAULT_BGCOLOR);
                    //    ((KartePanel2M)karte.getUI()).getTimeStampLabel().setBackground(DEFAULT_BGCOLOR);
                    //    ((KartePanel2M)karte.getUI()).getTimeStampLabel().setForeground(DEFAULT_FGCOLOR);
                    //}
//s.oh$
                }
                selectedKarte.setSelected(true);
//s.oh^ 2013/01/29 過去カルテの修正操作(選択状態)
                //if(selectedKarte.getUI() instanceof KartePanel2M) {
                //    // アイコンの差し換え(デザイン変更)
                //    ((KartePanel2M)selectedKarte.getUI()).getTimeStampPanel().setBackground(SELECTED_BGCOLOR);
                //    ((KartePanel2M)selectedKarte.getUI()).getTimeStampLabel().setBackground(SELECTED_BGCOLOR);
                //    ((KartePanel2M)selectedKarte.getUI()).getTimeStampLabel().setForeground(SELECTED_FGCOLOR);
                //}
//s.oh$
                stateMgr.processCleanEvent();

            } else {
                // selectedKarte == null
                stateMgr.processEmptyEvent();
            }
        }
    }

    /**
     * 新規カルテ作成の元になるカルテを返す。
     * @return 作成の元になるカルテ
     */
    public KarteViewer getBaseKarte() {
        KarteViewer ret = getSelectedKarte();
        if (ret == null) {
            if (karteList != null && karteList.size() > 0) {
                ret = ascending ? karteList.get(karteList.size() - 1) : karteList.get(0);
            }
        }
        return ret;
    }

    /**
     * 文書履歴の抽出期間が変更された場合、
     * karteList をclear、選択されているkarteViewerを解除、sateMgrをNoKarte状態に設定する。
     */
    @Override
    public void historyPeriodChanged() {
        if (karteList != null) {
            karteList.clear();
        }
        setSelectedKarte(null);
        getContext().showDocument(0);
    }

    /**
     * GUIコンポーネントにリスナを設定する。
     *
     */
    private void connect() {

        // 文書履歴に昇順／降順、修正履歴表示の設定をする
        // この値の初期値はデフォル値であり、個々のドキュメント（画面）単位にメニューで変更できる。（適用されるのは個々のドキュメントのみ）
        // デフォルト値の設定は環境設定で行う。
        ascending = getContext().getDocumentHistory().isAscending();
        showModified = getContext().getDocumentHistory().isShowModified();
    }

    /**
     * KarteViewerを生成し表示する。
     *
     * @param selectedHistories 文書履歴テーブルで選択された文書情報 DocInfo 配列
     * @param scroller
     */
    @Override
    public void showDocuments(DocInfoModel[] selectedHistories, final JScrollPane scroller) {

        if (selectedHistories == null || selectedHistories.length == 0) {
            return;
        }

        // 現在のリストと比較し、新たに追加されたもの、削除されたものに分ける
        ArrayList<DocInfoModel> added = new ArrayList<>(1); // 追加されたもの
        if (removed == null) {
            removed = new ArrayList<>(1); // 選択が解除されているもの
        } else {
            removed.clear();
        }

        // 追加されたものと選択を解除されたものに分ける

        // 1. 選択リストにあって 現在の karteList にないものは追加する
        for (DocInfoModel selectedDocInfo : selectedHistories) {
            boolean found = false;
            for (KarteViewer viewer : karteList) {
                if (viewer.getModel().getDocInfoModel().equals(selectedDocInfo)) {
                    found = true;
                    break;
                }
            }
            if (found == false) {
                added.add(selectedDocInfo);
            }
        }

        // 2 karteList にあって選択リストにないものはkarteListから削除する
        for (KarteViewer viewer : karteList) {
            boolean found = false;
            for (DocInfoModel selectedDocInfo : selectedHistories) {
                if (viewer.getModel().getDocInfoModel().equals(selectedDocInfo)) {
                    found = true;
                    break;
                }
            }
            if (found == false) {
                removed.add(viewer);
            }
        }

        // 解除されたものがあればそれをリストから取り除く
        if (removed != null && removed.size() > 0) {
            removed.stream().forEach((karte) -> {
                karteList.remove(karte);
            });
        }
        
//s.oh^ 2013/06/13 カルテ履歴が複数の場合、カルテ削除メニューを無効
        if(karteList != null && karteList.size() > 0) {
            boolean canEdit = !isReadOnly();
            boolean singleSelected = (karteList.size() == 1);
            getContext().enabledAction(GUIConst.ACTION_DELETE, (!showModified && canEdit && singleSelected));
        }
//s.oh$

        // 追加されたものがない場合
        if (added.isEmpty()) {

            boolean vsc = Project.getBoolean(Project.KARTE_SCROLL_DIRECTION, true);

            if (scrollerPanel != null) {
                scrollerPanel.removeAll();
            }

            scrollerPanel = new JPanel();

            if (vsc) {
                scrollerPanel.setLayout(new BoxLayout(scrollerPanel, BoxLayout.Y_AXIS));
            } else {
                scrollerPanel.setLayout(new BoxLayout(scrollerPanel, BoxLayout.X_AXIS));
            }

            karteList.stream().forEach((view) -> {
                scrollerPanel.add(view.getUI());
            });

            scroller.setViewportView(scrollerPanel);

            if (vsc) {
                showKarteListV();
            } else {
                showKarteListH();
            }
            
//s.oh^ 2013/08/22 正しくカルテ履歴が選択されない修正
            if(karteList != null && karteList.size() > 0) {
                // 選択状態にする
                setSelectedKarte(karteList.get(0));
            }
//s.oh$

            return;
        }

        // 取得する文書のID(PK)をリストを生成する
        List<Long> docId = new ArrayList<>(added.size());
        added.stream().forEach((bean) -> {
            docId.add(bean.getDocPk());
        });

        // データベースから取得する
        DocumentDelegater ddl = new DocumentDelegater();
        KarteTask task = new KarteTask(getContext(), docId, added, ddl, scroller);
        task.execute();
    }

    private KarteViewer createKarteViewer(DocInfoModel docInfo) {
        if (docInfo != null && docInfo.getDocType().equals(IInfoModel.DOCTYPE_S_KARTE)) {
            return new KarteViewer();
        }
        return new KarteViewer2();
    }

    /**
     * データベースで検索した KarteModelを Viewer で表示する。
     *
     * @param models KarteModel
     * @param docInfos DocInfo
     */
    private void addKarteViewer(List<DocumentModel> models, List<DocInfoModel> docInfos, final JScrollPane scroller) {

        if (models != null) {

            int index = 0;
            for (DocumentModel karteModel : models) {

                karteModel.setDocInfoModel(docInfos.get(index++)); // ?

                // シングル及び２号用紙の判定を行い、KarteViewer を生成する
                final KarteViewer karteViewer = createKarteViewer(karteModel.getDocInfoModel());
                karteViewer.setContext(getContext());
                karteViewer.setModel(karteModel);
                karteViewer.setAvoidEnter(true);

                // このコールでモデルのレンダリングが開始される
                karteViewer.start();

                // 2号カルテの場合ダブルクリックされたカルテを別画面で表示する
                // MouseListener を生成して KarteViewer の Pane にアタッチする
                if (karteModel.getDocInfoModel().getDocType().equals(IInfoModel.DOCTYPE_KARTE)) {
                    final MouseListener ml = new MouseAdapter() {

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            int cnt = e.getClickCount();
                            if (cnt == 2) {
                                //-----------------------------------
                                // 選択した Karte を EditoFrame で開く
                                //-----------------------------------
                                setSelectedKarte(karteViewer);
                                openKarte();
                            } else if (cnt == 1) {
                                setSelectedKarte(karteViewer);
                            }
                        }
                    };
                    karteViewer.addMouseListener(ml);
//s.oh^ 2013/01/29 過去カルテの修正操作(選択状態)
                    if(karteViewer.getUI() instanceof KartePanel2M) {
                        // アイコンの差し換え(デザイン変更)
                        ((KartePanel2M)karteViewer.getUI()).getTimeStampPanel().addMouseListener(ml);
                        ((KartePanel2M)karteViewer.getUI()).getTimeStampLabel().addMouseListener(ml);
                    }
//s.oh$
                }

                karteList.add(karteViewer);

//s.oh^ 2013/01/29 過去カルテの修正操作(選択状態)
                //if(karteViewer.getUI() instanceof KartePanel2M) {
                //    // アイコンの差し換え(デザイン変更)
                //    ((KartePanel2M)karteViewer.getUI()).getTimeStampPanel().setBackground(DEFAULT_BGCOLOR);
                //    ((KartePanel2M)karteViewer.getUI()).getTimeStampLabel().setBackground(DEFAULT_BGCOLOR);
                //    ((KartePanel2M)karteViewer.getUI()).getTimeStampLabel().setForeground(DEFAULT_FGCOLOR);
                //}
//s.oh$
            }
            // 時間軸でソート、viewへ通知、選択処理をする
            if (ascending) {
                Collections.sort(karteList);
            } else {
                Collections.sort(karteList, Collections.reverseOrder());
            }

            // 選択する
            if (karteList.size() > 0) {
                if (ascending) {
                    setSelectedKarte(karteList.get(karteList.size() - 1));
//s.oh^ 2013/01/29 過去カルテの修正操作(選択状態)
                    //if(karteList.get(karteList.size() - 1).getUI() instanceof KartePanel2M) {
                    //    // アイコンの差し換え(デザイン変更)
                    //    ((KartePanel2M)karteList.get(karteList.size() - 1).getUI()).getTimeStampPanel().setBackground(SELECTED_BGCOLOR);
                    //    ((KartePanel2M)karteList.get(karteList.size() - 1).getUI()).getTimeStampLabel().setBackground(SELECTED_BGCOLOR);
                    //    ((KartePanel2M)karteList.get(karteList.size() - 1).getUI()).getTimeStampLabel().setForeground(SELECTED_FGCOLOR);
                    //}
//s.oh$
                } else {
                    setSelectedKarte(karteList.get(0));
//s.oh^ 2013/01/29 過去カルテの修正操作(選択状態)
                    //if(karteList.get(0).getUI() instanceof KartePanel2M) {
                    //    // アイコンの差し換え(デザイン変更)
                    //    ((KartePanel2M)karteList.get(0).getUI()).getTimeStampPanel().setBackground(SELECTED_BGCOLOR);
                    //    ((KartePanel2M)karteList.get(0).getUI()).getTimeStampLabel().setBackground(SELECTED_BGCOLOR);
                    //    ((KartePanel2M)karteList.get(0).getUI()).getTimeStampLabel().setForeground(SELECTED_FGCOLOR);
                    //}
//s.oh$
                }
            }
        }

        boolean vsc = Project.getBoolean(Project.KARTE_SCROLL_DIRECTION, true);

        if (scrollerPanel != null) {
            scrollerPanel.removeAll();
        }

        scrollerPanel = new JPanel();
        
        if (vsc) {
            scrollerPanel.setLayout(new BoxLayout(scrollerPanel, BoxLayout.Y_AXIS));
        } else {
            scrollerPanel.setLayout(new BoxLayout(scrollerPanel, BoxLayout.X_AXIS));
        }

        int index = 0;
        for (KarteViewer view : karteList) {
            
            if (vsc) {
                view.getUI().setAlignmentY(Component.LEFT_ALIGNMENT);
            } else {
                view.getUI().setAlignmentY(Component.TOP_ALIGNMENT);
            }
            
            scrollerPanel.add(view.getUI());
//s.oh^ 2013/01/29 複数カルテの表示(区切り線)
            if(Project.getBoolean(Project.KARTE_SCROLL_DIRECTION, false)) {
                //scrollerPanel.add(new JSeparator(JSeparator.HORIZONTAL));
            }else{
                scrollerPanel.add(new JSeparator(JSeparator.VERTICAL));
            }
//s.oh$
            index++;
        }       
        
        scroller.setViewportView(scrollerPanel);

        if (vsc) {
            showKarteListV();
        } else {
            showKarteListH();
        }
        
//s.oh^ 2013/06/13 カルテ履歴が複数の場合、カルテ削除メニューを無効
    if(karteList != null) {
        boolean canEdit = !isReadOnly();
        boolean singleSelected = (karteList.size() == 1);
        getContext().enabledAction(GUIConst.ACTION_DELETE, (!showModified && canEdit && singleSelected));
    }
//s.oh$
    }

    private void showKarteListV() {

        Runnable awt = () -> {
            if (karteList.size() > 1) {
                int totalHeight = 0;
                for (KarteViewer view : karteList) {
                    int w = view.panel2.getPreferredSize().width;
//minagawa^ Kuroiwa specific                        
//s.oh^ 2013/03/28 入力行が一行の場合に文字が全部表示されない。
                    //int h = view.getActualHeight() + 30;
//s.oh^ 2014/06/02 複数カルテ表示時に全部表示されない
                    //int h = view.getActualHeight() + KARTE_OFFSET_HEIGHT;
                    int offset = (view.getModel().getModules() != null) ? view.getModel().getModules().size() * 20 : 0;
                    int h = view.getActualHeight() + KARTE_OFFSET_HEIGHT + offset;
//s.oh$
//s.oh$
                    //int h = view.getActualHeight() + KARTE_OFFSET_HEIGHT; -<kuroiwa specific
//minagawa$
                    totalHeight += h;
                    view.panel2.setPreferredSize(new Dimension(w, h));
                }
                int spWidth = scrollerPanel.getPreferredSize().width;
                scrollerPanel.setPreferredSize(new Dimension(spWidth, totalHeight));
            }
            
            scrollerPanel.scrollRectToVisible(new Rectangle(0, 0, scrollerPanel.getWidth(), 100));
            //scrollerPanel.setVisible(true);
            getContext().showDocument(0);
            if (removed != null) {
                removed.stream().forEach((karte) -> {
                    karte.stop();
                });
                removed.clear();
            }
        };
        EventQueue.invokeLater(awt);
    }

    private void showKarteListH() {

        Runnable awt = () -> {
            if (karteList.size() > 1) {
                int maxHeight = 0;
                for (KarteViewer view : karteList) {
                    int w = view.panel2.getPreferredSize().width;
                    int h = view.getActualHeight() + 20;
                    maxHeight = maxHeight >= h ? maxHeight : h;
                    view.panel2.setPreferredSize(new Dimension(w, h));
                }
                int spWidth = scrollerPanel.getPreferredSize().width;
                scrollerPanel.setPreferredSize(new Dimension(spWidth, maxHeight));
            }
            
            scrollerPanel.scrollRectToVisible(new Rectangle(0, 0, scrollerPanel.getWidth(), 100));
            getContext().showDocument(0);
            if (removed != null) {
                removed.stream().forEach((karte) -> {
                    karte.stop();
                });
                removed.clear();
            }
        };
        EventQueue.invokeLater(awt);
    }
    
    // (予定カルテ対応)
    private boolean isKarte() {
        if (getBaseKarte()==null) {
            return false;
        }
        String docType = getBaseKarte().getModel().getDocInfoModel().getDocType();
        return IInfoModel.DOCTYPE_KARTE.equals(docType);
    }
    
    // (予定カルテ対応)
    private boolean hasModule() {
        return (getBaseKarte().getModel().getModules()!=null && !getBaseKarte().getModel().getModules().isEmpty());
    }
    
    // (予定カルテ対応)
    private boolean hasSendModule() {
        // 送信するものがあるか
        boolean hasSend = false;
        List<ModuleModel> mList = getBaseKarte().getModel().getModules();
        for (ModuleModel m : mList) {
            if (m.getModuleInfoBean().getStampRole().equals(IInfoModel.ROLE_P)) {
                hasSend = true;
                break;
            }
        }
        return hasSend;
    }
    
    // (予定カルテ対応)
    private boolean hasPrescription() {
        // 処方があるか
        boolean hasSend = false;
        List<ModuleModel> mList = getBaseKarte().getModel().getModules();
        for (ModuleModel m : mList) {
            if (m.getModuleInfoBean().getEntity().equals(IInfoModel.ENTITY_MED_ORDER)) {
                hasSend = true;
                break;
            }
        }
        return hasSend;
    }

    /**
     * 表示されているカルテを CLAIM 送信する
     * 元町皮ふ科
     */
    public void sendClaim() {

        boolean send = isKarte() && hasModule() && hasSendModule();
        if (!send) {
            return;
        }
        // 新規文書モデルを修正モードで作成する
        //final DocumentModel model = getContext().getKarteModelToEdit(getBaseKarte().getModel());
        final DocumentModel model = getBaseKarte().getModel().claimClone();
        if (model==null) {
            return;
        }        
        model.setKarteBean(getContext().getKarte());
        model.setUserModel(Project.getUserModel());
        model.getDocInfoModel().setConfirmDate(new Date());
        model.getDocInfoModel().setSendClaim(true);
        // (予定カルテ対応)
        model.getDocInfoModel().setFacilityName(Project.getUserModel().getFacilityModel().getFacilityName());
        model.getDocInfoModel().setCreaterLisence(Project.getUserModel().getLicenseModel().getLicense());
        
        if (Project.canAccessToOrca() && Project.claimSenderIsClient()) {
            ClaimSender claimSender = new ClaimSender();
            claimSender.setContext(getContext());
            claimSender.prepare(model);
            claimSender.send(model);
            
        } else if (Project.canAccessToOrca() && Project.claimSenderIsServer()) {
            
            String pid = getContext().getPatientVisit().getPatientId();
            model.getDocInfoModel().setPatientId(pid);
            model.getDocInfoModel().setPatientName(getContext().getPatient().getFullName());
            model.getDocInfoModel().setPatientGender(getContext().getPatient().getGender());
            PVTHealthInsuranceModel pvtInsurance = getContext().getHealthInsuranceToApply(model.getDocInfoModel().getHealthInsuranceGUID());
            if (pvtInsurance!=null) {
                model.getDocInfoModel().setPVTHealthInsuranceModel(pvtInsurance);
            }
           
            DBTask task = new DBTask<Integer, Void>(getContext()) {

                @Override
                protected Integer doInBackground() throws Exception {

                    OrcaRestDelegater ord = new OrcaRestDelegater();
                    int cnt = ord.sendDocument(model);
                    return cnt;
                }

                @Override
                protected void succeeded(Integer result) {
                }
            };

            task.execute();
        }
    }

    /**
     * カルテを修正する。
     */
    public void modifyKarte() {

        if (getBaseKarte() == null) {
            return;
        }

//s.oh^ 2014/06/17 複数カルテ修正制御
        for (KarteEditor karte : KarteEditor.getAllKarte()) {
            if(karte.getContext().getPatient().getId() == getContext().getPatient().getId()) {
                if(!karte.checkModify()) {
                    return;
                }
            }
        }
//s.oh$
        
        String docType = getBaseKarte().getModel().getDocInfoModel().getDocType();
//s.oh^ 2014/08/21 修正時にアラート表示
        if(Project.getBoolean(Project.KARTE_SHOW_MODIFY_MSG)) {
            Calendar c1 = Calendar.getInstance();
            c1.setTime(new Date());
            Calendar c2 = Calendar.getInstance();
            c2.setTime(getBaseKarte().getModel().getStarted());
            if(c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR) || c1.get(Calendar.MONTH) != c2.get(Calendar.MONTH) || c1.get(Calendar.DATE) != c2.get(Calendar.DATE)) {
                
                java.util.ResourceBundle bundle = ClientContext.getMyBundle(KarteDocumentViewer.class);
                String cDateFmt = bundle.getString("dateFormat.started.modifyKarte");
                String question = bundle.getString("messageFormat.modifyKarte");
                String optionModify = bundle.getString("optionText.modifyKarte");
                String title = bundle.getString("title.optionPane.modifyKarte");
                
                SimpleDateFormat sdf = new SimpleDateFormat(cDateFmt);
                MessageFormat msft = new MessageFormat(question);
                
                String msg = msft.format(new Object[]{sdf.format(c2.getTime())});
                String[] btn = new String[]{optionModify, GUIFactory.getCancelButtonText()};
                
                int option = JOptionPane.showOptionDialog(
                        getContext().getFrame(),
                        msg,
                        ClientContext.getFrameTitle(title),
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        btn,
                        btn[1]);
                if(option != 0) {
                    return;
                }
            }
        }
//s.oh$

        ChartImpl chart = (ChartImpl) getContext();
        String deptName = getContext().getPatientVisit().getDeptName();
        String deptCode = getContext().getPatientVisit().getDeptCode();

        NewKarteParams params = new NewKarteParams(Chart.NewKarteOption.BROWSER_MODIFY);
        params.setDocType(docType);
        params.setDepartmentName(deptName);
        params.setDepartmentCode(deptCode);
        // このフラグはカルテを別ウインドウで編集するかどうか
        params.setOpenFrame(Project.getBoolean(Project.KARTE_PLACE_MODE, true));

        DocumentModel editModel = chart.getKarteModelToEdit(getBaseKarte().getModel());
        KarteEditor editor = chart.createEditor();
        editor.setModel(editModel);
        editor.setEditable(true);
        editor.setModify(true);
        int mode = docType.equals(IInfoModel.DOCTYPE_KARTE) ? KarteEditor.DOUBLE_MODE : KarteEditor.SINGLE_MODE;
        editor.setMode(mode);

        // Single Karte の場合 EF させない
        if (mode == 1) {
            params.setOpenFrame(false);
        }

        if (params.isOpenFrame()) {
            EditorFrame editorFrame = new EditorFrame();
            editorFrame.setChart(getContext());
//s.oh^ 2014/06/17 複数カルテ修正制御
            editor.setEditorFrame(editorFrame);
//s.oh$
            editorFrame.setKarteEditor(editor);
            editorFrame.start();
        } else {
            editor.setContext(chart);
            editor.initialize();
            editor.start();
            String updateStr = ClientContext.getMyBundle(KarteDocumentViewer.class).getString("tabTitle.update");
            chart.addChartDocument(editor, updateStr);
        }
    }

//masuda^
    @Override
    public void print() {
        
        KarteViewer view = getSelectedKarte();
        if (view==null) {
            return;
        }
//        JCheckBox cb = new JCheckBox("PDFは昇順に印刷");
//        //cb.setSelected(ascending);
//        cb.setSelected(true);
//        Object[] msg = new Object[2];
////s.oh^ 2013/02/07 印刷対応
//        //msg[0] = "PDFファイルを作成しますか？";
//        //msg[1] = cb;
//        //int option = JOptionPane.showOptionDialog(
//        //        getContext().getFrame(),
//        //        msg,
//        //        ClientContext.getFrameTitle("カルテ印刷"),
//        //        JOptionPane.DEFAULT_OPTION,
//        //        JOptionPane.INFORMATION_MESSAGE,
//        //        null,
//        //        new String[]{"PDF作成", "イメージ印刷", GUIFactory.getCancelButtonText()},
//        //        "PDF作成");
//        //
//        //if (option == 0) {
//        //    makePDF(cb.isSelected());
//        //} else if (option == 1) {
//        //    printKarte();
//        //}
//        msg[0] = "印刷しますか？";
//        String[] btn = null;
//        if(Project.getBoolean(Project.KARTE_PDF_SEND_AT_SAVE)) {
//            btn = new String[]{"PDF作成", "イメージ印刷", GUIFactory.getCancelButtonText()};
//        }else{
//            btn = new String[]{"イメージ印刷", GUIFactory.getCancelButtonText()};
//        }
//        int option = JOptionPane.showOptionDialog(
//                getContext().getFrame(),
//                msg,
//                ClientContext.getFrameTitle("カルテ印刷"),
//                JOptionPane.DEFAULT_OPTION,
//                JOptionPane.INFORMATION_MESSAGE,
//                null,
//                btn,
//                "PDF作成");
//        Log.outputFuncLog(Log.LOG_LEVEL_0, Log.FUNCTIONLOG_KIND_OTHER, ClientContext.getFrameTitle("カルテ印刷"), msg[0].toString(), String.valueOf(cb.isSelected()));
//
//        if(Project.getBoolean(Project.KARTE_PDF_SEND_AT_SAVE)) {
//            if (option == 0) {
//                Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, "PDF作成");
//                makePDF(false);
//            } else if (option == 1) {
//                if(Project.getBoolean(Project.KARTE_PRINT_PDF)) {
//                    Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, "PDF作成");
//                    printPDF();
//                }else{
//                    Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, "イメージ印刷");
//                    printKarte();
//                }
//            } else {
//                Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, "取消し");
//            }
//        }else{
//            if (option == 0) {
//                if(Project.getBoolean(Project.KARTE_PRINT_PDF)) {
//                    Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, "PDF作成");
//                    printPDF();
//                }else{
//                    Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, "イメージ印刷");
//                    printKarte();
//                }
//            } else {
//                Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, "取消し");
//            }
//        }
        java.util.ResourceBundle bundle = ClientContext.getMyBundle(KarteDocumentViewer.class);
        String checkBoxText = bundle.getString("checkBoxText.pdfOrder");
        String questionPrint = bundle.getString("question.print");
        String optionCreatePDF = bundle.getString("optionText.createPDF");
        String optionImagePrint = bundle.getString("optionText.imagePrint");
        String title = bundle.getString("title.optionPane.print");
        
        JCheckBox cb = new JCheckBox(checkBoxText);
        cb.setSelected(true);
        Object[] msg = new Object[2];
        
        msg[0] = questionPrint;
        String[] btn;
        if(Project.getBoolean(Project.KARTE_PDF_SEND_AT_SAVE)) {
            btn = new String[]{optionCreatePDF, optionImagePrint, GUIFactory.getCancelButtonText()};
        }else{
            btn = new String[]{optionImagePrint, GUIFactory.getCancelButtonText()};
        }
        int option = JOptionPane.showOptionDialog(
                getContext().getFrame(),
                msg,
                ClientContext.getFrameTitle(title),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                btn,
                optionCreatePDF);
        
        if(Project.getBoolean(Project.KARTE_PDF_SEND_AT_SAVE)) {
            if (option == 0) {
                makePDF(false);
            } else if (option == 1) {
                if(Project.getBoolean(Project.KARTE_PRINT_PDF)) {
                    printPDF();
                }else{
                    printKarte();
                }
            } else {
            }
        }else{
            if (option == 0) {
                if(Project.getBoolean(Project.KARTE_PRINT_PDF)) {
                    printPDF();
                }else{
                    printKarte();
                }
            }
        }
    }

    // カルテのPDFを作成する
    private void makePDF(boolean asc) {

//        List<DocumentModel> docList = new ArrayList<DocumentModel>();
//        for (KarteViewer viewer : karteList) {
//            docList.add(viewer.getModel());
//        }
        List<DocumentModel> docList = new ArrayList<>();
        docList.add(getSelectedKarte().getModel());

        KartePDFMaker maker = new KartePDFMaker();
        maker.setContext(getContext());
        maker.setDocumentList(docList);
        maker.setAscending(asc);
        maker.create();
    }

    // インスペクタに表示されているカルテをまとめて印刷する。
    private void printKarte() {
//        // ブザイクなんだけど、あまり使わない機能なのでこれでヨシとする
//        // 背景色が緑だとインクがもったいないので白にする。選択も解除しておく。
//        for (DocInfoModel docInfo : docInfoArray) {
//            KarteViewer viwer = karteViewerMap.get(docInfo.getDocPk());
//            viwer.setBackground(Color.WHITE);
//        }
//        selectedKarte.setSelected(false);
//
//        // 患者名を取得
//        String name = getContext().getPatient().getFullName();
//        String id = getContext().getPatient().getPatientId();
//        // scrollerPanelを印刷する
//        PrintKarteDocumentView.printComponent(scrollerPanel, name, id);
//
//        // 背景色を戻しておく
//        for (DocInfoModel docInfo : docInfoArray) {
//            KarteViewer viwer = karteViewerMap.get(docInfo.getDocPk());
//            viwer.setBackground(viwer.getSOAPane().getUneditableColor());
//        }
//        setSelectedKarte(null);
        KarteViewer view = getSelectedKarte();
        if (view!=null) {
            view.print();
        }
    }
//masuda$

//s.oh^ 2013/02/07 印刷対応
    private void printPDF() {
        StringBuilder sb = new StringBuilder();
        sb.append(ClientContext.getTempDirectory());
        //sb.append(File.separator);
        //sb.append("Print.pdf");
        //KartePDFImpl2 pdf = new KartePDFImpl2
        KarteViewer2 karte = null;
        KartePaneDumper_2 dumper = null;
        KartePaneDumper_2 pdumper = null;
        ArrayList<String> paths = new ArrayList<>(0);
//s.oh^ 2014/03/19 カルテの選択印刷
        
        java.util.ResourceBundle clBundle = ClientContext.getClaimBundle();
        
        if(Project.getBoolean(Project.KARTE_PRINT_MULTI) && !Project.getBoolean(Project.KARTE_PRINT_SHOWPDF)) {
            int id = 0;
            for(KarteViewer view : karteList) {
                String docNo = String.format("%04d", id++);
                if(view instanceof KarteViewer2) {
                    karte = (KarteViewer2)view;
                    dumper = new KartePaneDumper_2();
                    pdumper = new KartePaneDumper_2();
                    KarteStyledDocument doc = (KarteStyledDocument)karte.getSOAPane().getTextPane().getDocument();
                    dumper.dump(doc);
                    KarteStyledDocument pdoc = (KarteStyledDocument)karte.getPPane().getTextPane().getDocument();
                    pdumper.dump(pdoc);
                }
                if(karte != null && dumper != null && pdumper != null) {
                    StringBuilder sbTitle = new StringBuilder();
                    sbTitle.append(karte.getTimeStampLabel().getText());
                    
                    if(getSelectedKarte().getModel().getDocInfoModel().getHealthInsurance().startsWith(clBundle.getString("INSURANCE_SELF_PREFIX"))) {
                        //sbTitle.append("（自費）");
                        String selfInsurance = ClientContext.getMyBundle(KarteDocumentViewer.class).getString("text.selfInsurance");
                        sbTitle.append(selfInsurance);
                    }
                    KartePDFImpl2 pdf = new KartePDFImpl2(sb.toString(), null,
                                                          karte.getContext().getPatient().getPatientId(), karte.getContext().getPatient().getFullName(),
                                                          sbTitle.toString(),
                                                          new Date(), dumper, pdumper, docNo);
                    paths.add(pdf.create());
                }
                karte = null;
                dumper = null;
                pdumper = null;
            }
        }else{
//s.oh$
            if(getSelectedKarte() instanceof KarteViewer2) {
                karte = (KarteViewer2)getSelectedKarte();
                dumper = new KartePaneDumper_2();
                pdumper = new KartePaneDumper_2();
                KarteStyledDocument doc = (KarteStyledDocument)karte.getSOAPane().getTextPane().getDocument();
                dumper.dump(doc);
                KarteStyledDocument pdoc = (KarteStyledDocument)karte.getPPane().getTextPane().getDocument();
                pdumper.dump(pdoc);
            }
            if(karte != null && dumper != null && pdumper != null) {
//s.oh^ 2013/06/14 自費の場合、印刷時に文言を付加する
                //KartePDFImpl2 pdf = new KartePDFImpl2(sb.toString(), null,
                //                                      karte.getContext().getPatient().getPatientId(), karte.getContext().getPatient().getFullName(),
                //                                      karte.getTimeStampLabel().getText(),
                //                                      new Date(), dumper, pdumper);
                StringBuilder sbTitle = new StringBuilder();
                sbTitle.append(karte.getTimeStampLabel().getText());
                if(getSelectedKarte().getModel().getDocInfoModel().getHealthInsurance().startsWith(clBundle.getString("INSURANCE_SELF_PREFIX"))) {
                    //sbTitle.append("（自費）");
                    String selfInsurance = ClientContext.getMyBundle(KarteDocumentViewer.class).getString("text.selfInsurance");
                    sbTitle.append(selfInsurance);
                }
                KartePDFImpl2 pdf = new KartePDFImpl2(sb.toString(), null,
                                                      karte.getContext().getPatient().getPatientId(), karte.getContext().getPatient().getFullName(),
                                                      sbTitle.toString(),
                                                      new Date(), dumper, pdumper, null);
//s.oh$
                paths.add(pdf.create());
                //File file = new File(path);
                //if(file.exists()) {
                //    try {
                //        Desktop.getDesktop().print(file);
                //    } catch (IOException ex) {
                //        Logger.getLogger(KarteDocumentViewer.class.getName()).log(Level.SEVERE, null, ex);
                //    }
                //}
            }
        }
        if(paths.size() > 0) {
            KartePDFImpl2.printPDF(paths);
        }
        if(!Project.getBoolean(Project.KARTE_PRINT_SHOWPDF)) {
            for(String path : paths) {
                File file = new File(path);
                if(file.exists()) {
                    file.delete();
                }
            }
        }
    }
//s.oh$
    
    /**
     * 昇順表示にする。
     */
    public void ascending() {
        ascending = true;
        getContext().getDocumentHistory().setAscending(ascending);
    }

    /**
     * 降順表示にする。
     */
    public void descending() {
        ascending = false;
        getContext().getDocumentHistory().setAscending(ascending);
    }

    /**
     * 修正履歴の表示モードにする。
     */
    public void showModified() {
        showModified = !showModified;
        getContext().getDocumentHistory().setShowModified(showModified);
    }

    /**
     * karteList 内でダブルクリックされたカルテ（文書）を EditorFrame で開く。
     *
     */
    public void openKarte() {

        if (getSelectedKarte() != null) {

            // EditorFrameを生成する
            EditorFrame editorFrame = new EditorFrame();
            editorFrame.setChart(getContext());

            // 表示している文書タイプに応じて Viewer を作成する
            DocumentModel model = getSelectedKarte().getModel();
            String docType = model.getDocInfoModel().getDocType();

            if (docType.equals(IInfoModel.DOCTYPE_S_KARTE)) {
                // plain文書をEditorFrameに設定する
                KarteViewer view = new KarteViewer();
                view.setModel(model);
                editorFrame.setKarteViewer(view);
                editorFrame.start();
            } else if (docType.equals(IInfoModel.DOCTYPE_KARTE)) {
                // 2号カルテをEditorFrameに設定する
                KarteViewer2 view = new KarteViewer2();
                view.setModel(model);
                editorFrame.setKarteViewer(view);
                editorFrame.start();
            }
        }
    }

    /**
     * 表示選択されているカルテを論理削除する。
     * 患者を間違えた場合等に履歴に表示されないようにするため。
     */
    public void delete() {

        // 対象のカルテを得る
        KarteViewer delete = getBaseKarte();
        if (delete == null) {
            return;
        }
//        // Dialog を表示し理由を求める
////s.oh^ 2013/08/13
//        //String message = "このドキュメントを削除しますか ?   ";
//        String karteTitle = (delete.getTimeStampLabel() != null) ? delete.getTimeStampLabel().getText() : "カルテ";
//        //String message = "このドキュメントを削除しますか ?   \n" + karteTitle;
//        String message = new MessageFormat("このドキュメントを削除しますか ? \n{0}").format(new Object[]{karteTitle});
////s.oh$
//        final JCheckBox box1 = new JCheckBox("作成ミス");
//        final JCheckBox box2 = new JCheckBox("診察キャンセル");
//        final JCheckBox box3 = new JCheckBox("その他");
//        box1.setSelected(true);
//
//        ActionListener al = new ActionListener() {
//
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                if (box1.isSelected() || box2.isSelected()) {
//                } else if (!box3.isSelected()) {
//                    box3.setSelected(true);
//                }
//            }
//        };
//
//        box1.addActionListener(al);
//        box2.addActionListener(al);
//        box3.addActionListener(al);
//
//        Object[] msg = new Object[5];
//        msg[0] = message;
//        msg[1] = box1;
//        msg[2] = box2;
//        msg[3] = box3;
//        msg[4] = new JLabel(" ");
//        String deleteText = "削除する";
////minagawa^ mac jdk7        
//        //String cancelText = (String) UIManager.get("OptionPane.cancelButtonText");
//        String cancelText = GUIFactory.getCancelButtonText();
////minagawa$
//        int option = JOptionPane.showOptionDialog(
//                this.getUI(),
//                msg,
//                ClientContext.getFrameTitle("ドキュメント削除"),
//                JOptionPane.DEFAULT_OPTION,
//                JOptionPane.WARNING_MESSAGE,
//                null,
//                new String[]{deleteText, cancelText},
//                cancelText);
//        Log.outputFuncLog(Log.LOG_LEVEL_0, Log.FUNCTIONLOG_KIND_OTHER, ClientContext.getFrameTitle("ドキュメント削除"),
//                          message, box1.getText(), String.valueOf(box1.isSelected()),
//                          message, box2.getText(), String.valueOf(box2.isSelected()),
//                          message, box3.getText(), String.valueOf(box3.isSelected()));
//
//        //System.out.println(option);
//
//        // キャンセルの場合はリターンする
//        if (option != 0) {
//            Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, cancelText);
//            return;
//        }
//        Log.outputOperLogDlg(getContext(), Log.LOG_LEVEL_0, deleteText);
//        //
//        // 削除する status = 'D'
//        //
//        long deletePk = delete.getModel().getId();
//        DocumentDelegater ddl = new DocumentDelegater();
//        DeleteTask task = new DeleteTask(getContext(), deletePk, ddl);
//        task.execute();
        // Dialog を表示し理由を求める
        java.util.ResourceBundle bundle = ClientContext.getMyBundle(KarteDocumentViewer.class);
        String titleKarte = bundle.getString("title.karte");
        String deleteQuestion = bundle.getString("messageFormat.deleteKarte");
        String reasonMiss = bundle.getString("reasonText.miss");
        String reasonCancel = bundle.getString("reasonText.cancel");
        String reasonOther = bundle.getString("reasonText.other");
        String optionDelete = bundle.getString("optionText.delete");
        String title = bundle.getString("title.optionPane.deleteKarte");
        
        String karteTitle = (delete.getTimeStampLabel() != null) ? delete.getTimeStampLabel().getText() : titleKarte;
        String message = new MessageFormat(deleteQuestion).format(new Object[]{karteTitle});
        
        final JCheckBox box1 = new JCheckBox(reasonMiss);
        final JCheckBox box2 = new JCheckBox(reasonCancel);
        final JCheckBox box3 = new JCheckBox(reasonOther);
        box1.setSelected(true);
        ActionListener al = (ActionEvent e) -> {
            if (box1.isSelected() || box2.isSelected()) {
            } else if (!box3.isSelected()) {
                box3.setSelected(true);
            }
        };
        box1.addActionListener(al);
        box2.addActionListener(al);
        box3.addActionListener(al);

        Object[] msg = new Object[5];
        msg[0] = message;
        msg[1] = box1;
        msg[2] = box2;
        msg[3] = box3;
        msg[4] = new JLabel(" ");
        
        int option = JOptionPane.showOptionDialog(
                this.getUI(),
                msg,
                ClientContext.getFrameTitle(title),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new String[]{optionDelete, GUIFactory.getCancelButtonText()},
                GUIFactory.getCancelButtonText());

        // キャンセルの場合はリターンする
        if (option != 0) {
            return;
        }
        //
        // 削除する status = 'D'
        //
        long deletePk = delete.getModel().getId();
        DocumentDelegater ddl = new DocumentDelegater();
        DeleteTask task = new DeleteTask(getContext(), deletePk, ddl);
        task.execute();       
    }
    
    /**
     * 処方箋を印刷する
     * 新宿ヒロクリニックのポーティング
     */   
    public void createPrescription() {
        
        boolean ok = isKarte() && hasModule() && hasPrescription();
        if (!ok) {
            return;
        }       
        DocumentModel model = getBaseKarte().getModel();
        PrescriptionMaker maker = new PrescriptionMaker();
        maker.setChart(getContext());
        maker.setDocumentModel(model);
        maker.start();
    }  
    
    /**
     * 文書をデータベースから取得するタスククラス。
     */
    class KarteTask extends DBTask<List<DocumentModel>, Void> {

        private final DocumentDelegater ddl;
        private final List<Long> docId;
        private final List<DocInfoModel> docInfos;
        private final JScrollPane scroller;

        public KarteTask(Chart ctx, List<Long> docId, List<DocInfoModel> docInfos, DocumentDelegater ddl, JScrollPane scroller) {
            super(ctx);
            this.docId = docId;
            this.ddl = ddl;
            this.docInfos = docInfos;
            this.scroller = scroller;
        }

        @Override
        protected List<DocumentModel> doInBackground() throws Exception {
            java.util.logging.Logger.getLogger(this.getClass().getName()).fine("カルテタスク doInBackground");
            List<DocumentModel> result = ddl.getDocuments(docId);
            java.util.logging.Logger.getLogger(this.getClass().getName()).fine("doInBackground noErr, return result");
            return result;
        }

        @Override
        protected void succeeded(List<DocumentModel> list) {
            java.util.logging.Logger.getLogger(this.getClass().getName()).fine("KarteTask succeeded");
            if (list != null) {
                addKarteViewer(list, docInfos, scroller);
            }
        }
    }

    /**
     * カルテの削除タスククラス。
     */
    class DeleteTask extends DBTask<Boolean, Void> {

        private final DocumentDelegater ddl;
        private final long docPk;

        public DeleteTask(Chart ctx, long docPk, DocumentDelegater ddl) {
            super(ctx);
            this.docPk = docPk;
            this.ddl = ddl;
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            java.util.logging.Logger.getLogger(this.getClass().getName()).fine("DeleteTask started");
            ddl.deleteDocument(docPk);
            return true;
        }

        @Override
        protected void succeeded(Boolean result) {
            java.util.logging.Logger.getLogger(this.getClass().getName()).fine("DeleteTask succeeded");
            Chart chart = (KarteDocumentViewer.this).getContext();
            chart.getDocumentHistory().getDocumentHistory();
        }
    }

    /**
     * 抽象状態クラス。
     */
    protected abstract class BrowserState {

        public BrowserState() {
        }

        public abstract void enter();
    }

    /**
     * 表示するカルテがない状態を表す。
     */
    protected final class EmptyState extends BrowserState {

        public EmptyState() {
        }

        @Override
        public void enter() {
            boolean canEdit = !isReadOnly();
            getContext().enabledAction(GUIConst.ACTION_NEW_KARTE, canEdit);         // 新規カルテ
            getContext().enabledAction(GUIConst.ACTION_NEW_DOCUMENT, canEdit);      // 新規文書
            getContext().enabledAction(GUIConst.ACTION_MODIFY_KARTE, false);        // 修正
            getContext().enabledAction(GUIConst.ACTION_DELETE, false);              // 削除
            getContext().enabledAction(GUIConst.ACTION_PRINT, false);               // 印刷   
            getContext().enabledAction(GUIConst.ACTION_ASCENDING, false);           // 昇順
            getContext().enabledAction(GUIConst.ACTION_DESCENDING, false);          // 降順
            getContext().enabledAction(GUIConst.ACTION_SHOW_MODIFIED, false);       // 修正履歴表示
            getContext().enabledAction(GUIConst.ACTION_SEND_CLAIM, false);           // CLAIM送信
            getContext().enabledAction(GUIConst.ACTION_CREATE_PRISCRIPTION, false); // 処方箋印刷
            getContext().enabledAction(GUIConst.ACTION_CHECK_INTERACTION, false);   // 併用禁忌チェック
        }
    }

    /**
     * カルテが表示されている状態を表す。
     */
    protected final class ClaenState extends BrowserState {

        public ClaenState() {
        }

        @Override
        public void enter() {

            //-----------------------------------------
            // 新規カルテが可能なケース 仮保存でないことを追加
            //-----------------------------------------
            boolean canEdit = !isReadOnly();
            
            KarteViewer base = getBaseKarte();
            boolean hasSendModule = isKarte() && hasModule() && hasSendModule();
            
            boolean tmpKarte = false;
            if (base != null) {
                String state = base.getModel().getDocInfoModel().getStatus();
                if (state.equals(IInfoModel.STATUS_TMP)) {
                    tmpKarte = true;
                }
            }
//s.oh^ 2014/10/24 新規カルテメニューの制御
//            boolean newOk = canEdit && (!tmpKarte) ? true : false;
////minagawa^ 予定カルテ            (予定カルテ対応)
//            if (!newOk && canEdit) {
//                List<DocInfoModel> list = getContext().getKarte().getDocInfoList();
//                if(list != null) {
//                    boolean allTmp = true;
//                    for (DocInfoModel dinfo : list) {
//                        if (!dinfo.getStatus().equals(IInfoModel.STATUS_TMP)) {
//                            allTmp = false;
//                            break;
//                        }
//                    }
//                    if (allTmp) {
//                        newOk = true;
//                    }
//                }else{
//                    newOk = true;
//                }
//            }
////minagawa$            
            boolean newOk = canEdit;
//s.oh$
            getContext().enabledAction(GUIConst.ACTION_NEW_KARTE, newOk);        // 新規カルテ
            getContext().enabledAction(GUIConst.ACTION_NEW_DOCUMENT, canEdit);   // 新規文書
            getContext().enabledAction(GUIConst.ACTION_MODIFY_KARTE, canEdit);   // 修正
            // delete^
//s.oh^ 2013/06/13 カルテ履歴が複数の場合、カルテ削除メニューを無効
            //getContext().enabledAction(GUIConst.ACTION_DELETE, (!showModified && canEdit)); // 削除 履歴表示中
            boolean singleSelected = (karteList.size() == 1);
            getContext().enabledAction(GUIConst.ACTION_DELETE, (!showModified && canEdit && singleSelected));
//s.oh$
//s.oh^ 2014/08/19 ID権限
            //getContext().enabledAction(GUIConst.ACTION_PRINT, true);             // 印刷
            getContext().enabledAction(GUIConst.ACTION_PRINT, !Project.isOtherCare());
            getContext().enabledAction(GUIConst.ACTION_PRINTER_SETUP, !Project.isOtherCare());
//s.oh$
            getContext().enabledAction(GUIConst.ACTION_ASCENDING, true);         // 昇順
            getContext().enabledAction(GUIConst.ACTION_DESCENDING, true);        // 降順
            getContext().enabledAction(GUIConst.ACTION_SHOW_MODIFIED, true);     // 修正履歴表示
            getContext().enabledAction(GUIConst.ACTION_CHECK_INTERACTION, Project.canSearchMaster());   // 併用禁忌チェック

            //-----------------------------------------
            // CLAIM 送信が可能なケース
            //-----------------------------------------
            boolean sendOk = getContext().isSendClaim();
            sendOk = sendOk && Project.canAccessToOrca();
            sendOk = sendOk && hasSendModule;           
            getContext().enabledAction(GUIConst.ACTION_SEND_CLAIM, sendOk);       // CLAIM送信
            
            // 処方箋印刷
            boolean createOk = isKarte() && hasModule() && hasPrescription();
            
            getContext().enabledAction(GUIConst.ACTION_CREATE_PRISCRIPTION, createOk); // 処方箋印刷
        }
    }

    /**
     * StateContext クラス。
     */
    protected final class StateMgr {

        private final BrowserState emptyState = new EmptyState();
        private final BrowserState cleanState = new ClaenState();
        private BrowserState currentState;

        public StateMgr() {
            currentState = emptyState;
        }

        public void processEmptyEvent() {
            currentState = emptyState;
            this.enter();
        }

        public void processCleanEvent() {
            currentState = cleanState;
            this.enter();
        }

        public void enter() {
            currentState.enter();
        }
    }
}
