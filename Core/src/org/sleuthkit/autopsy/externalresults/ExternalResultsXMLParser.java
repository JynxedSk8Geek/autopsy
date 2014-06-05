/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.externalresults;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.ErrorInfo;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.datamodel.Content;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses an XML representation of of results data (e.g., artifacts, derived
 * files, reports) generated by a process external to Autopsy.
 */
public final class ExternalResultsXMLParser implements ExternalResultsParser {

    private static final Logger logger = Logger.getLogger(ExternalResultsXMLParser.class.getName());
    private static final String XSD_FILE = "autopsy_external_results.xsd"; //NON-NLS
    private final Content dataSource;
    private final String resultsFilePath;
    private ExternalResults resultsData;
    private List<ErrorInfo> errors = new ArrayList<>();

    /**
     * Tag names for an external results XML file.
     */
    public enum TagNames {

        ROOT_ELEM("autopsy_results"), //NON-NLS
        DERIVED_FILES_LIST_ELEM("derived_files"), //NON-NLS
        DERIVED_FILE_ELEM("derived_file"), //NON-NLS
        LOCAL_PATH_ELEM("local_path"), //NON-NLS
        PARENT_FILE_ELEM("parent_file"), //NON-NLS
        ARTIFACTS_LIST_ELEM("artifacts"), //NON-NLS
        ARTIFACT_ELEM("artifact"), //NON-NLS
        SOURCE_FILE_ELEM("source_file"), //NON-NLS
        ATTRIBUTE_ELEM("attribute"), //NON-NLS
        VALUE_ELEM("value"), //NON-NLS
        SOURCE_MODULE_ELEM("source_module"), //NON-NLS
        REPORTS_LIST_ELEM("reports"), //NON-NLS
        REPORT_ELEM("report"), //NON-NLS
        REPORT_NAME_ELEM("report_name"); //NON-NLS
        private final String text;

        private TagNames(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }
    }

    /**
     * Attribute names for an external results XML file.
     */
    public enum AttributeNames {

        TYPE_ATTR("type"); //NON-NLS
        private final String text;

        private AttributeNames(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }
    }

    /**
     * Attribute values for an external results XML file.
     */
    public enum AttributeValues {

        VALUE_TYPE_TEXT("text"), //NON-NLS
        VALUE_TYPE_INT32("int32"), //NON-NLS
        VALUE_TYPE_INT64("int64"), //NON-NLS
        VALUE_TYPE_DOUBLE("double"); //NON-NLS
        private final String text;

        private AttributeValues(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }
    }

    /**
     * Constructor.
     *
     * @param importFilePath Full path of the results file to be parsed.
     */
    public ExternalResultsXMLParser(Content dataSource, String resultsFilePath) {
        this.dataSource = dataSource;
        this.resultsFilePath = resultsFilePath;
    }

    @Override
    public ExternalResults parse() {
        this.errors.clear();
        this.resultsData = new ExternalResults(dataSource);
        try {
            // Note that XMLUtil.loadDoc() logs a warning if the file does not
            // conform to the XSD, but still returns a Document object. Until 
            // this behavior is improved, validation is still required. If 
            // XMLUtil.loadDoc() does return null, it failed to load the 
            // document and it logged the error.
            final Document doc = XMLUtil.loadDoc(ExternalResultsXMLParser.class, this.resultsFilePath, XSD_FILE);
            if (doc != null) {
                final Element rootElem = doc.getDocumentElement();
                if (rootElem != null && rootElem.getNodeName().equals(TagNames.ROOT_ELEM.toString())) {
                    parseDerivedFiles(rootElem);
                    parseArtifacts(rootElem);
                    parseReports(rootElem);
                } else {
                    String errorMessage = String.format("Did not find %s root element of %s", TagNames.ROOT_ELEM.toString(), this.resultsFilePath);
                    recordError(errorMessage);
                }
            }
        } catch (Exception ex) {
            String errorMessage = String.format("Error parsing %s", this.resultsFilePath);
            recordError(errorMessage, ex);
        }
        return this.resultsData;
    }

    @Override
    public List<ErrorInfo> getErrorInfo() {
        return new ArrayList<>(this.errors);
    }

    private void parseDerivedFiles(Element rootElement) {
        // Get the derived file lists.
        NodeList derivedFilesListNodes = rootElement.getElementsByTagName(TagNames.DERIVED_FILES_LIST_ELEM.toString());
        for (int i = 0; i < derivedFilesListNodes.getLength(); ++i) {
            Element derivedFilesListElem = (Element) derivedFilesListNodes.item(i);
            // Get the derived files.
            NodeList derivedFileNodes = derivedFilesListElem.getElementsByTagName(TagNames.DERIVED_FILE_ELEM.toString());
            for (int j = 0; j < derivedFileNodes.getLength(); ++j) {
                Element derivedFileElem = (Element) derivedFileNodes.item(j);
                // Get the local path of the derived file.
                String path = getChildElementContent(derivedFileElem, TagNames.LOCAL_PATH_ELEM.toString(), true);
                if (path.isEmpty()) {
                    continue;
                }
                // Get the parent file of the derived file.
                String parentFile = getChildElementContent((Element) derivedFileNodes.item(j), TagNames.PARENT_FILE_ELEM.toString(), true);
                if (parentFile.isEmpty()) {
                    continue;
                }
                this.resultsData.addDerivedFile(path, parentFile);
            }
        }
    }

    private void parseArtifacts(final Element root) {
        // Get the artifact lists.
        NodeList artifactsListNodes = root.getElementsByTagName(TagNames.ARTIFACTS_LIST_ELEM.toString());
        for (int i = 0; i < artifactsListNodes.getLength(); ++i) {
            Element artifactsListElem = (Element) artifactsListNodes.item(i);
            // Get the artifacts.
            NodeList artifactNodes = artifactsListElem.getElementsByTagName(TagNames.ARTIFACT_ELEM.toString());
            for (int j = 0; j < artifactNodes.getLength(); ++j) {
                Element artifactElem = (Element) artifactNodes.item(j);
                // Get the artifact type.
                final String type = getElementAttributeValue(artifactElem, AttributeNames.TYPE_ATTR.toString());
                if (!type.isEmpty()) {
                    // Get the source file of the artifact and the attributes,
                    // if any.
                    final String sourceFilePath = this.getChildElementContent((Element) artifactElem, TagNames.SOURCE_FILE_ELEM.toString(), true);
                    if (!sourceFilePath.isEmpty()) {
                        ExternalResults.Artifact artifact = this.resultsData.addArtifact(type, sourceFilePath);
                        parseArtifactAttributes(artifactElem, artifact);
                    }
                }
            }
        }
    }

    private void parseArtifactAttributes(final Element artifactElem, ExternalResults.Artifact artifact) {
        // Get the artifact attributes.
        NodeList attributeNodesList = artifactElem.getElementsByTagName(TagNames.ATTRIBUTE_ELEM.toString());
        for (int i = 0; i < attributeNodesList.getLength(); ++i) {
            Element attributeElem = (Element) attributeNodesList.item(i);
            final String type = getElementAttributeValue(attributeElem, AttributeNames.TYPE_ATTR.toString());
            if (type.isEmpty()) {
                continue;
            }
            // Get the value of the artifact attribute.
            Element valueElem = this.getChildElement(attributeElem, TagNames.VALUE_ELEM.toString());
            if (valueElem == null) {
                continue;
            }
            final String value = valueElem.getTextContent();
            if (value.isEmpty()) {
                String errorMessage = String.format("Found %s element that has no content in %s",
                        TagNames.VALUE_ELEM.toString(), this.resultsFilePath);
                recordError(errorMessage);
                continue;
            }
            // Get the value type.
            String valueType = parseArtifactAttributeValueType(valueElem);
            if (valueType.isEmpty()) {
                continue;
            }
            // Get the optional source module.
            String sourceModule = this.getChildElementContent(attributeElem, TagNames.SOURCE_MODULE_ELEM.toString(), false);
            // Add the attribute to the artifact.
            artifact.addAttribute(type, value, valueType, sourceModule);
        }
    }

    private String parseArtifactAttributeValueType(Element valueElem) {
        String valueType = valueElem.getAttribute(AttributeNames.TYPE_ATTR.toString());
        if (valueType.isEmpty()) {
            // Default to text.
            valueType = AttributeValues.VALUE_TYPE_TEXT.toString();
        } else if (!valueType.equals(AttributeValues.VALUE_TYPE_TEXT.toString()) 
                && !valueType.equals(AttributeValues.VALUE_TYPE_DOUBLE.toString())
                && !valueType.equals(AttributeValues.VALUE_TYPE_INT32.toString()) 
                && !valueType.equals(AttributeValues.VALUE_TYPE_INT64.toString())) {
            String errorMessage = String.format("Found unrecognized value %s for %s attribute of %s element",
                    valueType,
                    AttributeNames.TYPE_ATTR.toString(),
                    TagNames.VALUE_ELEM.toString());
            this.recordError(errorMessage);
            valueType = "";
        }
        return valueType;
    }

    private void parseReports(Element root) {
        // Get the report lists.
        NodeList reportsListNodes = root.getElementsByTagName(TagNames.REPORTS_LIST_ELEM.toString());
        for (int i = 0; i < reportsListNodes.getLength(); ++i) {
            Element reportsListElem = (Element) reportsListNodes.item(i);
            // Get the reports.
            NodeList reportNodes = reportsListElem.getElementsByTagName(TagNames.REPORT_ELEM.toString());
            for (int j = 0; j < reportNodes.getLength(); ++j) {
                Element reportElem = (Element) reportNodes.item(j);
                // Get the local path.
                String path = getChildElementContent(reportElem, TagNames.LOCAL_PATH_ELEM.toString(), true);
                if (path.isEmpty()) {
                    continue;
                }
                // Get the source module.
                String sourceModule = getChildElementContent(reportElem, TagNames.SOURCE_MODULE_ELEM.toString(), true);
                if (path.isEmpty()) {
                    continue;
                }
                // Get the optional report name.
                String reportName = getChildElementContent(reportElem, TagNames.REPORT_NAME_ELEM.toString(), false);
                this.resultsData.addReport(path, sourceModule, reportName);
            }
        }
    }

    private String getElementAttributeValue(Element element, String attributeName) {
        final String attributeValue = element.getAttribute(attributeName);
        if (attributeValue.isEmpty()) {
            logger.log(Level.SEVERE, "Found {0} element missing {1} attribute in {2}", new Object[]{
                element.getTagName(),
                attributeName,
                this.resultsFilePath});
        }
        return attributeValue;
    }

    private String getChildElementContent(Element parentElement, String childElementTagName, boolean required) {
        String content = "";
        Element childElement = this.getChildElement(parentElement, childElementTagName);
        if (childElement != null) {
            content = childElement.getTextContent();
            if (content.isEmpty()) {
                String errorMessage = String.format("Found %s element with %s child element that has no content in %s",
                        parentElement.getTagName(),
                        childElementTagName,
                        this.resultsFilePath);
                this.recordError(errorMessage);
            }
        } else if (required) {
            String errorMessage = String.format("Found %s element missing %s child element in %s",
                    parentElement.getTagName(),
                    childElementTagName,
                    this.resultsFilePath);
            this.recordError(errorMessage);
        }
        return content;
    }

    private Element getChildElement(Element parentElement, String childElementTagName) {
        Element childElem = null;
        NodeList childNodes = parentElement.getElementsByTagName(childElementTagName);
        if (childNodes.getLength() > 0) {
            childElem = (Element) childNodes.item(0);
            if (childNodes.getLength() > 1) {
                String errorMessage = String.format("Found multiple %s child elements for %s element in %s, ignoring all but first occurrence",
                        childElementTagName,
                        parentElement.getTagName(),
                        this.resultsFilePath);
                this.recordError(errorMessage);
            }
        }
        return childElem;
    }

    private void recordError(String errorMessage) {
        ExternalResultsXMLParser.logger.log(Level.SEVERE, errorMessage);
        this.errors.add(new ErrorInfo(this.getClass().getSimpleName(), errorMessage));
    }

    private void recordError(String errorMessage, Exception ex) {
        ExternalResultsXMLParser.logger.log(Level.SEVERE, errorMessage, ex);
        this.errors.add(new ErrorInfo(this.getClass().getSimpleName(), errorMessage, ex));
    }
}
