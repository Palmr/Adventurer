package uk.co.palmr.adventurer.itextbug;

import com.itextpdf.text.factories.RomanAlphabetFactory;
import com.itextpdf.text.factories.RomanNumberFactory;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfNumberTree;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfString;

import java.util.HashMap;

public class FixedPdfPageLabels {
      /**
     * Retrieves the page labels from a PDF as an array of String objects.
     * @param reader a PdfReader object that has the page labels you want to retrieve
     * @return a String array or <code>null</code> if no page labels are present
     */
    public static String[] getPageLabels(PdfReader reader) {
        int n = reader.getNumberOfPages();
        PdfDictionary dict = reader.getCatalog();
        PdfDictionary labels = (PdfDictionary)PdfReader.getPdfObjectRelease(dict.get(PdfName.PAGELABELS));
        if (labels == null)
            return null;

        String[] labelStrings = new String[n];

        HashMap<Integer, PdfObject> numberTree = PdfNumberTree.readTree(labels);

        int pageCount = 1;
        Integer current;
        String prefix = "";
        char type = 'D';
        for (int i = 0; i < n; i++) {
            current = Integer.valueOf(i);
            if (numberTree.containsKey(current)) {
                PdfDictionary d = (PdfDictionary)PdfReader.getPdfObjectRelease(numberTree.get(current));
                if (d.contains(PdfName.ST)) {
                    pageCount = ((PdfNumber)d.get(PdfName.ST)).intValue();
                }
                else {
                    pageCount = 1;
                }
                if (d.contains(PdfName.P)) {
                    prefix = ((PdfString)d.get(PdfName.P)).toUnicodeString();
                }
                else {
                    prefix = "";
                }
                if (d.contains(PdfName.S)) {
                    type = ((PdfName)d.get(PdfName.S)).toString().charAt(1);
                }
                else {
                    type = 'e';
                }
            }
            switch(type) {
                default:
                    labelStrings[i] = prefix + pageCount;
                    break;
                case 'R':
                    labelStrings[i] = prefix + RomanNumberFactory.getUpperCaseString(pageCount);
                    break;
                case 'r':
                    labelStrings[i] = prefix + RomanNumberFactory.getLowerCaseString(pageCount);
                    break;
                case 'A':
                    labelStrings[i] = prefix + RomanAlphabetFactory.getUpperCaseString(pageCount);
                    break;
                case 'a':
                    labelStrings[i] = prefix + RomanAlphabetFactory.getLowerCaseString(pageCount);
                    break;
                case 'e':
                    labelStrings[i] = prefix;
                    break;
            }
            pageCount++;
        }
        return labelStrings;
    }
}
