package controllers;

import play.*;
import play.libs.Json;
import play.mvc.*;
import java.util.Random;

public class RandomNum extends Controller {

    private static Random randomGenerator = new Random();

    static class JsonResult {
        Integer result;

        public JsonResult(Integer result) {
            this.result = result;
        }

        public Integer getResult() {
            return result;
        }

        public void setResult(Integer result) {
            this.result = result;
        }
    }

    public static Result getRandomNum() {
        Integer randomInt = randomGenerator.nextInt(100);
        JsonResult jsonResult = new JsonResult(randomInt);
        return ok(Json.toJson(jsonResult));
    }
}
