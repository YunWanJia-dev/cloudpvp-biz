package me.ywj.cloudpvp.play.controller;

import me.ywj.cloudpvp.play.entity.GameMode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/modes")
public class ModeController {
    @GetMapping
    public GameMode getMode() {
        return new GameMode();
    }
}
