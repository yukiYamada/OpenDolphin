package open.dolphin.client;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.awt.datatransfer.*;

import javax.swing.*;

import open.dolphin.delegater.LaboDelegater;
import open.dolphin.infomodel.LaboImportSummary;
import open.dolphin.server.LaboModuleBuilder;

/**
 * LaboTestFileTransferHandler
 *
 * @author kazushi Minagawa
 *
 */
class LaboTestFileTransferHandler extends TransferHandler {
    
    private static final long serialVersionUID = 2942768324728994019L;
    
    private DataFlavor fileFlavor;
    private LaboTestImporter context;
    private LinkedList<List<File>> queue;
    private ImportThread importThread;
    
    public LaboTestFileTransferHandler(LaboTestImporter context) {
        fileFlavor = DataFlavor.javaFileListFlavor;
        this.context = context;
        queue = new LinkedList<List<File>>();
        importThread = new ImportThread();
        importThread.start();
    }
    
    @SuppressWarnings("unchecked")
    public boolean importData(JComponent c, Transferable t) {
        
        if (!canImport(c, t.getTransferDataFlavors())) {
            return false;
        }
        
        try {
            if (hasFileFlavor(t.getTransferDataFlavors())) {
                
                java.util.List<File> files = (java.util.List<File>) t.getTransferData(fileFlavor);
                
                List<File> xmlFiles = new ArrayList<File>(files.size());
                
                for (File file : files) {
                    
                    if (!file.isDirectory() && file.getName().endsWith(".xml")) {
                        xmlFiles.add(file);
                    }
                }
                
                if (xmlFiles != null && xmlFiles.size() > 0) {
                    addFiles(xmlFiles);
                }
                
                return true;
            }
            
        } catch (UnsupportedFlavorException ufe) {
            ufe.printStackTrace();
            
        } catch (IOException ieo) {
            ieo.printStackTrace();
            
        }
        return false;
    }
    
    public boolean canImport(JComponent c, DataFlavor[] flavors) {
        if (hasFileFlavor(flavors)) {
            return true;
        }
        return false;
    }
    
    private boolean hasFileFlavor(DataFlavor[] flavors) {
        for (int i = 0; i < flavors.length; i++) {
            if (fileFlavor.equals(flavors[i])) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Queue�փh���b�v���ꂽ�t�@�C����������B
     * @param xmlFiles �h���b�v���ꂽ�t�@�C���̃��X�g
     */
    public synchronized void addFiles(List<File> xmlFiles) {
        queue.addLast(xmlFiles);
        notify();
    }
    
    /**
     * Queue����t�@�C�����X�g�����o���B
     * @return �h���b�v���ꂽ�t�@�C���̃��X�g
     */
    public synchronized List<File> getFiles() {
        
        while (queue.size() == 0) {
            try {
                wait();
            } catch (Exception e) {
            }
        }
        return (List<File>) queue.removeFirst();
    }
    
    /**
     * �t�@�C�����p�[�X���f�[�^�x�[�X�֓o�^����R���V���[�}�X���b�h�N���X�B
     */
    class ImportThread extends Thread {
        
        public void run() {
            while (! interrupted()) {
                try {
                    List<File> files = getFiles();
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            context.getUltraSonicProgressBar().start();
                        }
                    });
                    LaboModuleBuilder builder = new LaboModuleBuilder();
                    builder.setLogger(ClientContext.getLogger("laboTest"));
                    builder.setEncoding(ClientContext.getString("laboTestImport.mmlFile.encoding"));
                    builder.setLaboDelegater(new LaboDelegater());
                    final List<LaboImportSummary> result = builder.build(files);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            context.getUltraSonicProgressBar().stop();
                            context.getLaboListTable().getTableModel().addRows(result);
                            context.updateCount();
                        }
                    });
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}