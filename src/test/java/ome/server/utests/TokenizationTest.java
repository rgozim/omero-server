/*
 *   Copyright 2008 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.server.utests;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ome.services.fulltext.FullTextAnalyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import org.jmock.MockObjectTestCase;
import org.testng.annotations.Test;

@Test(timeOut = 1000) // Lucene initialization takes longer than default 200ms.
public class TokenizationTest extends MockObjectTestCase {

    void assertTokenizes(String text, String... tokens) {

        List<String> results = tokenize(text);
        assertEquals(tokens.length, results.size());
        for (int i = 0; i < tokens.length; i++) {
            String term = results.get(i);
            assertEquals(String.format("%s!=%s:%s", tokens[i], term,
                    results.toString()), tokens[i], term);
        }
    }

    @Test(groups = "ticket:3164")
    public void testProperHandlingOfFileNames() {
        assertTokenizes(".tif", "tif");
        assertTokenizes("*.tif", "tif");
        assertTokenizes("s*.tif", "s", "tif");
    }

    @Test
    public void testDefaults() {
        assertTokenizes("foo bar", "foo", "bar");
        assertTokenizes("foo/bar", "foo", "bar");
        assertTokenizes("foo||bar", "foo", "bar");
        assertTokenizes("foo;;bar", "foo", "bar");
        assertTokenizes("foo||bar;;qaz", "foo", "bar", "qaz");
        assertTokenizes("foo-bar", "foo", "bar");
        assertTokenizes("foo_bar", "foo", "bar");
        assertTokenizes("foo.bar", "foo", "bar");
        assertTokenizes("U.S.A.", "u", "s", "a");
        assertTokenizes("26.8.06-antiCSFV/CSFV-GFP/CSFV-GFP01_1_R3D_D3D.dv",
                "26", "8", "06", "anticsfv", "csfv", "gfp", "csfv", "gfp01",
                "1", "r3d", "d3d", "dv");
        assertTokenizes("...FRAP-23.8.05/IAGFP-Noc05_R3D.dv", "frap", "23",
                "8", "05", "iagfp", "noc05", "r3d", "dv");
        assertTokenizes("will/Desktop/CSFV-GFP01_3_R3D_D3D.dv", "will",
                "desktop", "csfv", "gfp01", "3", "r3d", "d3d", "dv");
        assertTokenizes("Documents/biology-data/CSFV-GFP01_3_R3D_D3D.dv",
                "documents", "biology", "data", "csfv", "gfp01", "3", "r3d",
                "d3d", "dv");
    }

    @Test
    public void testTokenizationWithQuery() throws Exception {
        IndexSearcher searcher = null;
        try {
            Directory directory = new RAMDirectory();
            Analyzer analyzer = new FullTextAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_31, analyzer);
            IndexWriter writer = new IndexWriter(directory, config);

            String[] docs = { "GFP-CSFV-abc", "GFP-H2B-123", "GFP_H2B-456" };
            addDocuments(writer, docs);

            searcher = new IndexSearcher(directory);

            Map<String, Integer> queryToResults = new HashMap();
            queryToResults.put("GFP", 3);
            queryToResults.put("GFP*", 3);
            queryToResults.put("GFP-H2B", 3);
            queryToResults.put("\"GFP H2B\"", 2);
            queryToResults.put("\"H2B GFP\"", 0);

            QueryParser parser = new QueryParser(Version.LUCENE_31, "contents", analyzer);
            for (String queryStr : queryToResults.keySet()) {
                Query query = parser.parse(queryStr);
                System.out.println("Query: " + query.toString("contents"));
                ScoreDoc[] hits = searcher.search(query, null, docs.length).scoreDocs;
                assertEquals(queryStr, queryToResults.get(queryStr).intValue(),
                        hits.length);
                System.out.println(hits.length + " total results");

            }
        } finally {
            if (searcher != null) {
                searcher.close();
            }
        }
    }

    // Helpers
    // =============================================================

    private void addDocuments(IndexWriter writer, String[] docs)
            throws IOException {
        for (int j = 0; j < docs.length; j++) {
            Document d = new Document();
            d.add(new Field("contents", docs[j], Field.Store.YES,
                    Field.Index.ANALYZED));
            writer.addDocument(d);
        }
        writer.close();
    }

    private List<String> tokenize(String a) {
        List<String> tokens = new ArrayList<>();
        Analyzer analyzer = new FullTextAnalyzer();
        try (TokenStream ts  = analyzer.tokenStream(null, new StringReader(a))) {
            ts.reset();  // required
            while (ts.incrementToken()) {
                tokens.add(ts.getAttribute(CharTermAttribute.class).toString());
            }
        } catch (IOException e) {
            new RuntimeException(e);  // Shouldn't happen...
        }
        return tokens;
    }
}
