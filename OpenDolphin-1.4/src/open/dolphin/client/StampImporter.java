package open.dolphin.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import open.dolphin.delegater.StampDelegater;
import open.dolphin.helper.SimpleWorker;
import open.dolphin.helper.WorkerService;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.PublishedTreeModel;
import open.dolphin.infomodel.SubscribedTreeModel;
import open.dolphin.project.Project;
import open.dolphin.table.ListTableModel;
import open.dolphin.table.OddEvenRowRenderer;

/**
 * StampImporter
 *
 * @author Minagawa,Kazushi
 */
public class StampImporter {
    
    private static final String[] COLUMN_NAMES = {
        "��  ��", "�J�e�S��", "���J��", "��  ��", "���J��", "�C���|�[�g"
    };
    private static final String[] METHOD_NAMES = {
        "name", "category", "partyName", "description", "publishType", "isImported"
    };
    private static final Class[] CLASSES = {
        String.class, String.class, String.class, String.class, String.class, Boolean.class
    };
    private static final int[] COLUMN_WIDTH = {
        120, 90, 170, 270, 40, 40
    };
    private static final Color ODD_COLOR = ClientContext.getColor("color.odd");
    private static final Color EVEN_COLOR = ClientContext.getColor("color.even");
    private static final ImageIcon WEB_ICON = ClientContext.getImageIcon("web_16.gif");
    private static final ImageIcon HOME_ICON = ClientContext.getImageIcon("home_16.gif");
    private static final ImageIcon FLAG_ICON = ClientContext.getImageIcon("flag_16.gif");
    
    private String title = "�X�^���v�C���|�[�g";
    private JFrame frame;
    private BlockGlass blockGlass;
    private JTable browseTable;
    private ListTableModel<PublishedTreeModel> tableModel;
    private JButton importBtn;
    private JButton deleteBtn;
    private JButton cancelBtn;
    private JLabel publicLabel;
    private JLabel localLabel;
    private JLabel importedLabel;
    
    private StampBoxPlugin stampBox;
    private List<Long> importedTreeList;

    // timerTask �֘A
    private javax.swing.Timer taskTimer;
    private ProgressMonitor monitor;
    private int delayCount;
    private int maxEstimation = 90*1000;    // 90 �b
    private int delay = 300;                // 300 mmsec
    
    public StampImporter(StampBoxPlugin stampBox) {
        this.stampBox = stampBox;
        importedTreeList = stampBox.getImportedTreeList();
    }

    /**
     * ���J����Ă���Tree�̃��X�g���擾���e�[�u���֕\������B
     */
    public void start() {

        final SimpleWorker worker = new SimpleWorker<List<PublishedTreeModel>, Void>() {

            @Override
            protected List<PublishedTreeModel> doInBackground() throws Exception {
                StampDelegater sdl = new StampDelegater();
                List<PublishedTreeModel> result = sdl.getPublishedTrees();
                if (!sdl.isNoError()) {
                    throw new Exception(sdl.getErrorMessage());
                } else {
                    return result;
                }
            }

            @Override
            protected void succeeded(List<PublishedTreeModel> result) {
                // DB����擾������������GUI�R���|�[�l���g�𐶐�����
                initComponent();
                if (importedTreeList != null && importedTreeList.size() > 0) {
                    for (PublishedTreeModel model : result) {
                        for (Long id : importedTreeList) {
                            if (id.longValue() == model.getId()) {
                                model.setImported(true);
                                break;
                            }
                        }
                    }
                }
                tableModel.setDataProvider(result);
            }

            @Override
            protected void failed(java.lang.Throwable cause) {
                JOptionPane.showMessageDialog(frame,
                            cause.getMessage(),
                            ClientContext.getFrameTitle(title),
                            JOptionPane.WARNING_MESSAGE);
                ClientContext.getBootLogger().warn(cause.getMessage());
            }
        };

        String message = "�X�^���v�C���|�[�g";
        String note = "���J�X�^���v���擾���Ă��܂�...";
        Component c = frame;
        monitor = new ProgressMonitor(c, message, note, 0, maxEstimation / delay);

        taskTimer = new Timer(delay, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                delayCount++;

                if (monitor.isCanceled() && (!worker.isCancelled())) {
                   // no cancel
                } else if (delayCount >= monitor.getMaximum() && (!worker.isCancelled())) {
                    worker.cancel(true);

                } else {
                    monitor.setProgress(delayCount);
                }
            }
        });

        WorkerService service = new WorkerService() {

            @Override
            protected void startProgress() {
                delayCount = 0;
                taskTimer.start();
            }

            @Override
            protected void stopProgress() {
                taskTimer.stop();
                monitor.close();
                taskTimer = null;
                monitor = null;
            }
        };

        service.execute(worker);
    }
    
    /**
     * GUI�R���|�[�l���g������������B
     */
    public void initComponent() {
        
        frame = new JFrame(ClientContext.getFrameTitle(title));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
            }
        });
        
        JPanel contentPane = createBrowsePane();
        contentPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 11, 11));
        
        contentPane.setOpaque(true);
        frame.setContentPane(contentPane);
        frame.pack();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int n = ClientContext.isMac() ? 3 : 2;
        int x = (screen.width - frame.getPreferredSize().width) / 2;
        int y = (screen.height - frame.getPreferredSize().height) / n;
        frame.setLocation(y, y);

        blockGlass = new BlockGlass();
        frame.setGlassPane(blockGlass);

        frame.setVisible(true);
    }
    
    /**
     * �I������B
     */
    public void stop() {
        frame.setVisible(false);
        frame.dispose();
    }
    
    /**
     * ���J�X�^���v�u���E�Y�y�C���𐶐�����B
     */
    private JPanel createBrowsePane() {
        
        JPanel browsePane = new JPanel();

        tableModel = new ListTableModel<PublishedTreeModel>(COLUMN_NAMES, 10, METHOD_NAMES, CLASSES);
        browseTable = new JTable(tableModel);
        for (int i = 0; i < COLUMN_WIDTH.length; i++) {
            browseTable.getColumnModel().getColumn(i).setPreferredWidth(COLUMN_WIDTH[i]);
        }
        browseTable.setDefaultRenderer(Object.class, new OddEvenRowRenderer());
        
        importBtn = new JButton("�C���|�[�g");
        importBtn.setEnabled(false);
        cancelBtn = new JButton("����");
        deleteBtn = new JButton("�폜");
        deleteBtn.setEnabled(false);
        publicLabel = new JLabel("�O���[�o��", WEB_ICON, SwingConstants.CENTER);
        localLabel = new JLabel("�@��", HOME_ICON, SwingConstants.CENTER);
        importedLabel = new JLabel("�C���|�[�g��", FLAG_ICON, SwingConstants.CENTER);

        JScrollPane tableScroller = new JScrollPane(browseTable);
        tableScroller.getViewport().setPreferredSize(new Dimension(730, 380));
        
        // ���C�A�E�g����
        browsePane.setLayout(new BorderLayout(0, 17));
        JPanel flagPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 5));
        flagPanel.add(localLabel);
        flagPanel.add(publicLabel);
        flagPanel.add(importedLabel);
        JPanel cmdPanel = GUIFactory.createCommandButtonPanel(new JButton[]{cancelBtn, deleteBtn, importBtn});
        browsePane.add(flagPanel, BorderLayout.NORTH);
        browsePane.add(tableScroller, BorderLayout.CENTER);
        browsePane.add(cmdPanel, BorderLayout.SOUTH);
        
        // �����_����ݒ肷��
        PublishTypeRenderer pubTypeRenderer = new PublishTypeRenderer();
        browseTable.getColumnModel().getColumn(4).setCellRenderer(pubTypeRenderer);
        ImportedRenderer importedRenderer = new ImportedRenderer();
        browseTable.getColumnModel().getColumn(5).setCellRenderer(importedRenderer);
        
        // BrowseTable���V���O���Z���N�V�����ɂ���
        browseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListSelectionModel sleModel = browseTable.getSelectionModel();
        sleModel.addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() == false) {
                    int row = browseTable.getSelectedRow();
                    PublishedTreeModel model = tableModel.getObject(row);
                    if (model != null) {
                        if (model.isImported()) {
                            importBtn.setEnabled(false);
                            deleteBtn.setEnabled(true);
                        } else {
                            importBtn.setEnabled(true);
                            deleteBtn.setEnabled(false);
                        }
                    } else {
                        importBtn.setEnabled(false);
                        deleteBtn.setEnabled(false);
                    }
                }
            }
        });

        // import
        importBtn.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                importPublishedTree();
            }
        });

        // remove
        deleteBtn.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                removeImportedTree();
            }
        });

        // �L�����Z��
        cancelBtn.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                stop();
            }
        });
        
        return browsePane;
    }
    
    /**
     * �u���E�U�e�[�u���őI���������JTree���C���|�[�g����B
     */
    public void importPublishedTree() {

        // �e�[�u���̓V���O���Z���N�V�����ł���
        int row = browseTable.getSelectedRow();
        final PublishedTreeModel importTree = tableModel.getObject(row);

        if (importTree == null) {
            return;
        }

        // Import �ς݂̏ꍇ
        if (importTree.isImported()) {
            return;
        }

        try {
            importTree.setTreeXml(new String(importTree.getTreeBytes(), "UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // �T�u�X�N���C�u���X�g�ɒǉ�����
        SubscribedTreeModel sm = new SubscribedTreeModel();
        sm.setUser(Project.getUserModel());
        sm.setTreeId(importTree.getId());
        final List<SubscribedTreeModel> subscribeList = new ArrayList<SubscribedTreeModel>(1);
        subscribeList.add(sm);

        final SimpleWorker worker = new SimpleWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                StampDelegater sdl = new StampDelegater();
                sdl.subscribeTrees(subscribeList);
                if (!sdl.isNoError()) {
                    throw new Exception(sdl.getErrorMessage());
                }
                return null;
            }

            @Override
            protected void succeeded(Void result) {
                // �X�^���v�{�b�N�X�փC���|�[�g����
                stampBox.importPublishedTree(importTree);
                // Browser�\�����C���|�[�g�ς݂ɂ���
                importTree.setImported(true);
                tableModel.fireTableDataChanged();
            }

            @Override
            protected void cancelled() {
                ClientContext.getBootLogger().debug("Task cancelled");
            }

            @Override
            protected void failed(java.lang.Throwable cause) {
                JOptionPane.showMessageDialog(frame,
                        cause.getMessage(),
                        ClientContext.getFrameTitle(title),
                        JOptionPane.WARNING_MESSAGE);
                ClientContext.getBootLogger().warn(cause.getMessage());
            }
        };

        String message = "�X�^���v�C���|�[�g";
        String note = "�C���|�[�g���Ă��܂�...";
        Component c = frame;
        monitor = new ProgressMonitor(c, message, note, 0, maxEstimation / delay);

        taskTimer = new Timer(delay, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                delayCount++;

                if (monitor.isCanceled() && (!worker.isCancelled())) {
                    //worker.cancel(true);
                    // No cancel

                } else if (delayCount >= monitor.getMaximum() && (!worker.isCancelled())) {
                    worker.cancel(true);

                } else {
                    monitor.setProgress(delayCount);
                }
            }
        });

        WorkerService service = new WorkerService() {

            @Override
            protected void startProgress() {
                delayCount = 0;
                blockGlass.block();
                taskTimer.start();
            }

            @Override
            protected void stopProgress() {
                taskTimer.stop();
                monitor.close();
                blockGlass.unblock();
                taskTimer = null;
                monitor = null;
            }
        };

        service.execute(worker);
    }
    
    /**
     * �C���|�[�g���Ă���X�^���v���폜����B
     */
    public void removeImportedTree() {

        // �폜����Tree���擾����
        int row = browseTable.getSelectedRow();
        final PublishedTreeModel removeTree = tableModel.getObject(row);
        
        if (removeTree == null) {
            return;
        }

        SubscribedTreeModel sm = new SubscribedTreeModel();
        sm.setTreeId(removeTree.getId());
        sm.setUser(Project.getUserModel());
        final List<SubscribedTreeModel> list = new ArrayList<SubscribedTreeModel>(1);
        list.add(sm);
        
        // Unsubscribe�^�X�N�����s����
        
        final SimpleWorker worker = new SimpleWorker<Void, Void>() {
  
            @Override
            protected Void doInBackground() throws Exception {
                StampDelegater sdl = new StampDelegater();
                sdl.unsubscribeTrees(list);
                if (!sdl.isNoError()) {
                    throw new Exception(sdl.getErrorMessage());
                }
                return null;
            }
            
            @Override
            protected void succeeded(Void result) {
                // �X�^���v�{�b�N�X����폜����
                stampBox.removeImportedTree(removeTree.getId());
                // �u���E�U�\����ύX����
                removeTree.setImported(false);
                tableModel.fireTableDataChanged();
            }
            
            @Override
            protected void cancelled() {
                ClientContext.getBootLogger().debug("Task cancelled");
            }
            
            @Override
            protected void failed(java.lang.Throwable cause) {
                JOptionPane.showMessageDialog(frame,
                            cause.getMessage(),
                            ClientContext.getFrameTitle(title),
                            JOptionPane.WARNING_MESSAGE);
                ClientContext.getBootLogger().warn(cause.getMessage());
            }
        };

        String message = "�X�^���v�C���|�[�g";
        String note = "�C���|�[�g�ς݃X�^���v���폜���Ă��܂�...";
        Component c = frame;
        monitor = new ProgressMonitor(c, message, note, 0, maxEstimation / delay);

        taskTimer = new Timer(delay, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                delayCount++;

                if (monitor.isCanceled() && (!worker.isCancelled())) {
                    //worker.cancel(true);
                    // No cancel

                } else if (delayCount >= monitor.getMaximum() && (!worker.isCancelled())) {
                    worker.cancel(true);

                } else {
                    monitor.setProgress(delayCount);
                }
            }
        });

        WorkerService service = new WorkerService() {

            @Override
            protected void startProgress() {
                delayCount = 0;
                blockGlass.block();
                taskTimer.start();
            }

            @Override
            protected void stopProgress() {
                taskTimer.stop();
                monitor.close();
                blockGlass.unblock();
                taskTimer = null;
                monitor = null;
            }
        };

        service.execute(worker);
    }
        
    class PublishTypeRenderer extends DefaultTableCellRenderer {
        
        /** Creates new IconRenderer */
        public PublishTypeRenderer() {
            super();
            setOpaque(true);
            setHorizontalAlignment(JLabel.CENTER);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value,
                boolean isSelected,
                boolean isFocused,
                int row, int col) {
            
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setForeground(table.getForeground());
                if (row % 2 == 0) {
                    setBackground(EVEN_COLOR);
                } else {
                    setBackground(ODD_COLOR);
                }
            }
            
            if (value != null && value instanceof String) {
                
                String pubType = (String) value;
                
                if (pubType.equals(IInfoModel.PUBLISHED_TYPE_GLOBAL)) {
                    setIcon(WEB_ICON);
                } else {
                    setIcon(HOME_ICON);
                } 
                this.setText("");
                
            } else {
                setIcon(null);
                this.setText(value == null ? "" : value.toString());
            }
            return this;
        }
    }
    
    class ImportedRenderer extends DefaultTableCellRenderer {
        
        /** Creates new IconRenderer */
        public ImportedRenderer() {
            super();
            setOpaque(true);
            setHorizontalAlignment(JLabel.CENTER);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value,
                boolean isSelected,
                boolean isFocused,
                int row, int col) {           
            
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setForeground(table.getForeground());
                if (row % 2 == 0) {
                    setBackground(EVEN_COLOR);
                } else {
                    setBackground(ODD_COLOR);
                }
            }
            
            if (value != null && value instanceof Boolean) {
                
                Boolean imported = (Boolean) value;
                
                if (imported.booleanValue()) {
                    this.setIcon(FLAG_ICON);
                } else {
                    this.setIcon(null);
                }
                this.setText("");
                
            } else {
                setIcon(null);
                this.setText(value == null ? "" : value.toString());
            }
            return this;
        }
    }
}