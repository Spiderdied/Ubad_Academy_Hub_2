package com.ubad.academy.data.backup

import java.util.Base64

/** Shared backup fixtures (a web-created v2 backup with every section). */
object BackupFixtures {
    val pdfBytes = ByteArray(70_000) { (it * 31 % 251).toByte() }
    val pngBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    /** Shaped exactly like app.js exportBackup() output (web-created backup). */
    val webBackup by lazy {
        """
        {"app":"ubad-academy-hub","version":2,"exportedAt":"2026-09-01T10:00:00.000Z",
         "sections":{"user":true,"courses":true,"notes":true,"calendar":true,"study":true,"islam":true,"summaries":true,"forms":true,"background":true},
         "data":{
          "user":{"name":"عبدالله"},
          "courses":[{"id":"c1","name":"فيزياء","code":"PHY101","instructor":"د. أحمد","credits":3.5,"semester":"الخريف","createdAt":1700000000000,
            "units":[{"id":"u1","title":"الوحدة الأولى","contents":[
              {"id":"x1","type":"pdf","title":"محاضرة","text":"","assetId":"a1","assets":[],"name":"lec.pdf","mime":"application/pdf","source":"","url":"","done":true,"createdAt":1700000000001},
              {"id":"x2","type":"video","title":"شرح","url":"https://youtu.be/dQw4w9WgXcQ","done":false,"createdAt":1700000000002},
              {"id":"x3","type":"image","title":"صور","assets":[{"id":"a2","name":"p.png","mime":"image/png"}],"createdAt":1700000000003},
              {"id":"x4","type":"text","title":"ملخص","text":"سطر\nسطر","createdAt":1700000000004}]},
             {"id":"u2","title":"Legacy","lessons":[{"id":"l1","title":"Old lesson","done":true}]}]}],
          "courseAssets":[{"id":"a1","name":"","type":"application/pdf","data":"data:application/pdf;base64,${b64(pdfBytes)}"},
                          {"id":"a2","name":"","type":"image/png","data":"data:image/png;base64,${b64(pngBytes)}"},
                          {"id":"bad","name":"","type":"x","data":"blob:nope"}],
          "notes":[{"id":"n1","title":"ملاحظة","body":"نص","tags":["فيزياء","exam"],"pin":true,"createdAt":1700000001000,"updatedAt":1700000002000,
                    "images":[{"name":"board.png","type":"image/png","data":"data:image/png;base64,${b64(pngBytes)}"}],"audio":[]},
                   {"id":"n2","title":"b","body":"","tags":[],"pin":false,"createdAt":1,"updatedAt":2,"images":[],"audio":[]}],
          "events":[{"id":"e1","title":"امتحان","desc":"","date":"2026-10-01","time":"09:30","createdAt":5},
                    {"id":"e2","title":"bad date","date":"tomorrow","time":"9:30","createdAt":6}],
          "decks":[{"id":"d1","title":"Deck","createdAt":7,"cards":[{"id":"k1","front":"F","back":"B"}]}],
          "quizzes":[{"id":"q1","title":"Quiz","createdAt":8,"questions":[
             {"q":"2+2?","options":["3","4","",""],"correct":1},
             {"q":"invalid","options":["only one"],"correct":0}]}],
          "schedule":[{"id":"s1","title":"Study","days":[6,0,"1",9],"start":"18:00","end":"19:30","doneDates":{"2026-09-01":true,"x":true},"createdAt":9}],
          "islam":{"day":"2026-09-01","prayers":{"fajr":2,"zuhr":1,"asr":5},"rawatib":{"witr":1},
                   "tasbih":{"mode":"my1","count":12,"total":99,"target":100,"adhkar":[{"id":"sub","text":"x","builtin":true},{"id":"my1","text":"لا إله إلا الله","builtin":false}]},
                   "fasts":["2026-08-31","nope"],"hist":{"2026-09-01":4}},
          "summaries":[{"id":"m1","title":"Sum","url":"docs.google.com/document/d/1","createdAt":10,"lastOpened":0,"pinned":true},
                       {"id":"m2","title":"js","url":"javascript:alert(1)","createdAt":11}],
          "forms":[{"id":"f1","title":"Form","url":"https://forms.gle/abc","createdAt":12,"lastOpened":13,"pinned":false}],
          "backgrounds":{"sage":{"type":"image/png","data":"data:image/png;base64,${b64(pngBytes)}"},"unknown":{"type":"image/png","data":"data:image/png;base64,AA=="}}
         }}
        """.trimIndent()
    }
}
