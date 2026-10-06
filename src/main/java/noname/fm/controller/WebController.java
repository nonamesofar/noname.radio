package noname.fm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Created by dfeodot on 10/10/2017.
 */
@Controller
public class WebController implements WebMvcConfigurer{

    @GetMapping("/")
    public String login() {
        return "login";
    }

    @PostMapping("/")
    public String player(@RequestParam(value="username") String user, @RequestParam(value="password") String pass) {
        return "playerV2";
    }

    @GetMapping("/player")
    public String play(){
        return "playerV2";
    }

}
