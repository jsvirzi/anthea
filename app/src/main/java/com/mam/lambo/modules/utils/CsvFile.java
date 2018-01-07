package com.mam.lambo.modules.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jsvirzi on 12/29/16.
 */

public class CsvFile {
    InputStream inputStream;

    public CsvFile(InputStream inputStream){
        this.inputStream = inputStream;
    }

    public CsvFile(String filename) throws FileNotFoundException {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(filename);
        } catch (FileNotFoundException ex) {
            String msg = String.format("unable to open file [%s]", filename);
            throw new FileNotFoundException(msg + ex);
        }
        inputStream = fileInputStream;
    }

    public List read() {
        List resultList = new ArrayList();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            String csvLine;
            while ((csvLine = reader.readLine()) != null) {
                String[] row = csvLine.split(",");
                resultList.add(row);
            }
        }
        catch (IOException ex) {
            throw new RuntimeException("error in reading CSV file: "+ex);
        }
        finally {
            try {
                inputStream.close();
            }
            catch (IOException e) {
                throw new RuntimeException("error while closing input stream: "+e);
            }
        }
        return resultList;
    }
}
