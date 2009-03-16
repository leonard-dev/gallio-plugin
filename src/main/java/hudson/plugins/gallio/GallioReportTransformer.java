package hudson.plugins.gallio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Transforms a Gallio report into seperate JUnit reports. The Gallio report can contain several test cases and the JUnit
 * report that is read by Hudson should only contain one. This class will split up one Gallio report into several JUnit
 * files.
 * 
 */
public class GallioReportTransformer implements TestReportTransformer, Serializable {

    private static final long serialVersionUID = 1L;
    
    public static final String JUNIT_FILE_POSTFIX = ".xml";
    public static final String JUNIT_FILE_PREFIX = "TEST-";

    private static final String TEMP_JUNIT_FILE_STR = "temp-junit.xml";
    public static final String GALLIO_TO_JUNIT_XSLFILE_STR = "gallio-to-junit.xsl";

    private transient boolean xslIsInitialized;
    private transient Transformer gallioTransformer;
    private transient Transformer writerTransformer;
    private transient DocumentBuilder xmlDocumentBuilder;

    /**
     * Transform the gallio file into several junit files in the output path
     * 
     * @param gallioFileStream the gallio file stream to transform
     * @param junitOutputPath the output path to put all junit files
     * @throws IOException thrown if there was any problem with the transform.
     * @throws TransformerException
     * @throws SAXException
     * @throws ParserConfigurationException 
     */
    public void transform(InputStream gallioFileStream, File junitOutputPath) throws IOException, TransformerException,
            SAXException, ParserConfigurationException {
        
        initialize();
        
        File junitTargetFile = new File(junitOutputPath, TEMP_JUNIT_FILE_STR);
        FileOutputStream fileOutputStream = new FileOutputStream(junitTargetFile);
        try {
            gallioTransformer.transform(new StreamSource(gallioFileStream), new StreamResult(fileOutputStream));
        } finally {
            fileOutputStream.close();
        }
        splitJUnitFile(junitTargetFile, junitOutputPath);
        junitTargetFile.delete();
    }

    private void initialize() throws TransformerFactoryConfigurationError, TransformerConfigurationException,
            ParserConfigurationException {
        if (!xslIsInitialized) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            gallioTransformer = transformerFactory.newTransformer(new StreamSource(this.getClass().getResourceAsStream(GALLIO_TO_JUNIT_XSLFILE_STR)));
            writerTransformer = transformerFactory.newTransformer();
    
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            xmlDocumentBuilder = factory.newDocumentBuilder();
            
            xslIsInitialized = true;
        }
    }

    /**
     * Splits the junit file into several junit files in the output path
     * 
     * @param junitFile report containing one or more junit test suite tags
     * @param junitOutputPath the path to put all junit files
     * @throws IOException
     * @throws SAXException
     * @throws TransformerException
     */
    private void splitJUnitFile(File junitFile, File junitOutputPath) throws SAXException, IOException,
            TransformerException {
        Document document = xmlDocumentBuilder.parse(junitFile);

        NodeList elementsByTagName = ((Element) document.getElementsByTagName("testsuites").item(0)).getElementsByTagName("testsuite");
        for (int i = 0; i < elementsByTagName.getLength(); i++) {
            Element element = (Element) elementsByTagName.item(i);
            DOMSource source = new DOMSource(element);
            File junitOutputFile = new File(junitOutputPath, JUNIT_FILE_PREFIX + element.getAttribute("name") + JUNIT_FILE_POSTFIX);
            FileOutputStream fileOutputStream = new FileOutputStream(junitOutputFile);
            try {
                StreamResult result = new StreamResult(fileOutputStream);
                writerTransformer.transform(source, result);
            } finally {
                fileOutputStream.close();
            }
        }
    }
}