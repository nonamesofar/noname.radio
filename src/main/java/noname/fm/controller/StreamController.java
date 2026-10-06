package noname.fm.controller;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import noname.fm.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by dfeodot on 9/18/2017.
 */
@Controller
public class StreamController {

    @Autowired
    MusicCollection mCollection;

    //test with http://localhost:8080/listen?id=7
    @RequestMapping(value="listen", produces = "audio/mpeg")
    public void streamFile(@RequestParam(value="id") int id, HttpServletRequest request, HttpServletResponse response) throws Exception {

        String filePath = mCollection.getCollection().get( id ).getPath();
        File file = new File(  filePath );

        MultipartFileSender.fromPath(file.toPath())
                .with(request)
                .with(response)
                .serveResource();
    }
}