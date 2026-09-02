/* ═══════════════════════════════════════════════════════════════
   UBAD ACADEMY HUB — application core
   state · storage (IndexedDB + localStorage) · spatial navigation
   i18n (en/ar) · audio · UI · sections · islam · backup · boot
   ═══════════════════════════════════════════════════════════════ */
'use strict';
(function(){

/* ═══ 1. utilities ═══════════════════════════════════════════ */
const $  = (s,r=document)=>r.querySelector(s);
const $$ = (s,r=document)=>Array.from(r.querySelectorAll(s));
const esc = s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const uid = ()=>Date.now().toString(36)+Math.random().toString(36).slice(2,8);
const wait = ms=>new Promise(r=>setTimeout(r,ms));
const RM = matchMedia('(prefers-reduced-motion: reduce)');
const ic = (n,c='')=>`<svg class="ic${c?' '+c:''}" aria-hidden="true" focusable="false"><use href="#i-${n}"></use></svg>`;
const loc = ()=>state.settings.lang==='ar'?'ar':'en';
const fmtDate=(d,o)=>{ try{ return new Intl.DateTimeFormat(loc(),o).format(d); }catch(e){ return d.toDateString(); } };
const fmtDateLong=d=>fmtDate(d,{weekday:'long',day:'numeric',month:'long',year:'numeric'});
const ymd=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const parseYmd=s=>{const [y,m,d]=s.split('-').map(Number);return new Date(y,m-1,d);};
const today=()=>ymd(new Date());
const normStr=(v,max,f='')=>typeof v==='string'?v.slice(0,max):(typeof v==='number'?String(v).slice(0,max):f);
const normArr=v=>Array.isArray(v)?v:[];
const clampNum=(v,min,max,f)=>{const n=Number(v);return isFinite(n)?Math.min(max,Math.max(min,n)):f;};
const MONO='"SF Mono",ui-monospace,Menlo,Consolas,monospace';
const cssVar=n=>getComputedStyle(document.documentElement).getPropertyValue(n).trim()||'#3B82F6';
function blobToDataURL(b){return new Promise((res,rej)=>{const r=new FileReader();r.onload=()=>res(r.result);r.onerror=rej;r.readAsDataURL(b);});}
const urlCache=new WeakMap();
const blobURL=b=>{ if(!urlCache.has(b)) urlCache.set(b,URL.createObjectURL(b)); return urlCache.get(b); };
const emptyState=(icon,msg,hint,btn)=>`<div class="empty">${ic(icon)}<p class="e-t">${esc(msg)}</p>${hint?`<p class="e-h">${esc(hint)}</p>`:''}${btn?`<button class="btn btn-primary" id="es-cta">${esc(btn)}</button>`:''}</div>`;
function buzz(ms){ try{ if(navigator.vibrate) navigator.vibrate(ms); }catch(e){} }

/* ═══ 2. i18n — hand-written EN / AR dictionary ══════════════ */
const I18N={
en:{
 'app.name':'UBAD ACADEMY HUB','hub.head':'Enter your academy',
 'hub.foot':'Local · Offline · Private','nav.hub':'Hub',
 'nav.dashboard':'Dashboard','nav.courses':'Courses','nav.notes':'Notes','nav.calendar':'Calendar',
 'nav.islam':'I am Muslim','nav.analytics':'Analytics','nav.study':'Study Tools','nav.settings':'Settings',
 'sub.dashboard':'Your day at a glance','sub.courses':'Your academic journey','sub.notes':'Ideas & study notes',
 'sub.calendar':'Schedule & events','sub.islam':'Prayers, adhkar & worship','sub.analytics':'Insights & statistics',
 'sub.study':'Flashcards & quizzes','sub.settings':'Personalize your hub',
 'common.add':'Add','common.save':'Save','common.cancel':'Cancel','common.delete':'Delete','common.edit':'Edit',
 'common.close':'Close','common.back':'Back','common.search':'Search','common.optional':'optional',
 'common.today':'Today','common.tomorrow':'Tomorrow','common.confirmDelete':'Delete this item? This cannot be undone.',
 'common.done':'Done','toast.saved':'Saved','toast.deleted':'Deleted','toast.error':'Something went wrong.',
 'dash.greeting':'Hello, {name}','dash.stPrayers':'Prayers','dash.stCourses':'Courses','dash.stTasks':'Open tasks','dash.stNotes':'Notes',
 'dash.tasks':'Tasks','dash.addTaskPh':'Add a quick task…','dash.newTask':'New task','dash.taskTitle':'Task title',
 'dash.taskDue':'Due date (optional)','dash.noTasks':'No open tasks — enjoy the calm.',
 'dash.upcoming':'Upcoming','dash.noEvents':'No upcoming events.','dash.recentNotes':'Recent notes','dash.noNotes':'No notes yet.',
 'dash.quick':'Quick actions','dash.newNote':'New note','dash.newEvent':'New event','dash.goStudy':'Study now','dash.overdue':'Overdue',
 'courses.new':'New course','courses.edit':'Edit course','courses.name':'Course name','courses.code':'Code',
 'courses.instructor':'Instructor','courses.credits':'Credits','courses.cr':'cr','courses.semester':'Semester',
 'courses.empty':'No courses yet','courses.emptyHint':'Create your first course to organize your semester.',
 'courses.lessonsLc':'lessons','courses.units':'Units','courses.newUnit':'New unit','courses.unitName':'Unit title',
 'courses.lessons':'Lessons','courses.newLesson':'New lesson','courses.lessonName':'Lesson title',
 'courses.noUnits':'No units yet. Add one to structure this course.','courses.noLessons':'No lessons in this unit yet.',
 'courses.deleteUnit':'Delete unit','courses.needName':'Give the course a name.',
 'notes.new':'New note','notes.searchPh':'Search notes…','notes.empty':'No notes found',
 'notes.emptyHint':'Write your first note — it stays on this device.',
 'notes.titlePh':'Note title','notes.bodyPh':'Start writing…','notes.tagsPh':'tags, comma separated',
 'notes.attachImage':'Add image','notes.attachAudio':'Add audio','notes.images':'Images','notes.audio':'Audio',
 'notes.deleteMsg':'Delete this note permanently?','notes.untitled':'Untitled note',
 'notes.discard':'Discard changes?','notes.discardMsg':'You have unsaved changes in this note.',
 'notes.discardBtn':'Discard','notes.tooBig':'File is larger than 5 MB.','notes.badType':'Unsupported file type.',
 'cal.new':'New event','cal.edit':'Edit event','cal.eventTitle':'Event title','cal.desc':'Description',
 'cal.time':'Time','cal.date':'Date','cal.none':'No events for this day.','cal.deleteMsg':'Delete this event?',
 'cal.needTitle':'Title and date are required.','cal.prev':'Previous month','cal.next':'Next month',
 'ana.gpaTrend':'GPA trend','ana.courseProgress':'Course progress','ana.tasksDonut':'Task completion',
 'ana.notesActivity':'Notes activity','ana.noData':'Not enough data yet.','ana.done':'Done','ana.pending':'Pending',
 'ana.stat.notes':'Notes','ana.stat.cards':'Flashcards','ana.stat.prayers':'Prayers today','ana.stat.quran':'Khatma progress',
 'ana.prayersTrend':'Prayer consistency (7 days)',
 'study.tabCards':'Flashcards','study.tabQuiz':'Quizzes','study.newDeck':'New deck','study.deckName':'Deck title',
 'study.noDecks':'No decks yet.','study.noDecksHint':'Create a deck and add flashcards to it.',
 'study.cardsLc':'cards','study.study':'Study','study.newCard':'Add card','study.front':'Front','study.back':'Back',
 'study.frontPh':'Question / prompt','study.backPh':'Answer','study.flip':'Flip','study.shuffle':'Shuffle',
 'study.prev':'Previous','study.next':'Next','study.deleteDeck':'Delete deck',
 'study.deleteDeckMsg':'Delete this deck and all of its cards?','study.editCard':'Edit card',
 'study.noCards':'This deck has no cards yet.','study.addFirst':'Add your first card to start studying.',
 'study.newQuiz':'New quiz','study.editQuiz':'Edit quiz','study.quizName':'Quiz title','study.noQuizzes':'No quizzes yet.',
 'study.noQuizzesHint':'Build a quiz with your own questions and answers.',
 'study.question':'Question','study.questionPh':'Type the question…','study.option':'Option {n}',
 'study.correctOpt':'Mark as correct answer','study.addQuestion':'Add question','study.removeQ':'Remove question',
 'study.start':'Start','study.qOf':'Question {i} of {n}','study.nextQ':'Next','study.finish':'Finish',
 'study.selectFirst':'Select an answer first.','study.score':'Your score','study.review':'Review',
 'study.your':'Your answer','study.correctAns':'Correct','study.retry':'Retry','study.questionsLc':'questions',
 'study.needTitle':'Give the quiz a title.','study.minQ':'A quiz needs at least one question.',
 'study.needQ':'Question {n} needs text, at least 2 options and a marked correct answer.',
 'set.language':'Language','set.langDesc':'Interface language and direction (LTR / RTL).',
 'set.profile':'Profile','set.username':'Username','set.usernamePh':'Your name',
 'set.usernameDesc':'Used only for the greeting on your dashboard — never renames your data.',
 'set.appearance':'Appearance','set.theme':'Theme','set.dark':'Dark','set.light':'Light',
 'set.th.dark':'Midnight','set.th.oled':'OLED Black','set.th.light':'Aurora',
 'set.th.paper':'Paper','set.th.sage':'Sage','set.th.rose':'Rose',
 'set.bg':'Theme backgrounds','set.bgDesc':'Upload your own background for each theme (max 5 MB). Stored offline on your device and shown softly behind the interface.',
 'set.bgUp':'Upload','set.bgRm':'Remove','set.bgApplied':'Background updated.','set.bgRemoved':'Background removed.',
 'set.sound':'Interface sounds','set.soundDesc':'Subtle feedback sounds. Falls back gracefully if audio files are not present.',
 'set.backup':'Backup & restore','set.backupDesc':'Export all of your data as a JSON file and restore it on any device. No account needed.',
 'set.export':'Export backup','set.import':'Import backup',
 'set.importConfirm':'Importing will replace ALL current data on this device. Continue?',
 'set.imported':'Backup restored successfully.','set.importFailed':'Invalid backup file.',
 'set.danger':'Danger zone',
 'set.clearMsg':'This permanently deletes all courses, notes, events, worship records and study content stored on this device.',
 'set.clearAll':'Erase all data','set.cleared':'All data erased.',
 'set.about':'About','set.aboutBody':'A local-first academic hub. No account, no server — your data stays with you.',
 'set.version':'Version','set.nameSaved':'Name updated.','set.needName':'Please enter a name.',
 'set.exported':'Backup file downloaded.',
 'search.ph':'Search notes, courses, events…','search.none':'No results for “{q}”',
 'search.notes':'Notes','search.courses':'Courses','search.events':'Events','search.decks':'Flashcards','search.quizzes':'Quizzes',
 'dash.greet.morning':'Good morning, {name}','dash.greet.afternoon':'Good afternoon, {name}',
 'dash.greet.evening':'Good evening, {name}','dash.greet.night':'Burning the midnight oil, {name}',
 'focus.tab':'Focus','focus.session':'Focus session','focus.break':'Break',
 'focus.start':'Start','focus.pause':'Pause','focus.reset':'Reset',
 'focus.today':'Sessions today','focus.length':'Session length','focus.min':'min',
 'focus.doneMsg':'Focus session complete — take a break','focus.breakOver':'Break over — ready for another round?',
 'notes.pin':'Pin note','notes.unpin':'Unpin note','notes.pinned':'Pinned',
 'lb.open':'View image','lb.zoomIn':'Zoom in','lb.zoomOut':'Zoom out','lb.reset':'Original size',
 'islam.peace':'Assalamu alaikum wa rahmatullah',
 'islam.prayers':'The five prayers','islam.p.fajr':'Fajr','islam.p.zuhr':'Dhuhr','islam.p.asr':'Asr',
 'islam.p.maghrib':'Maghrib','islam.p.isha':'Isha','islam.jamaah':'Congregation',
 'islam.prayersDone5':'All five prayers complete — may Allah accept them',
 'islam.rawatib':'Sunnah rawatib','islam.rak':'rak\'ahs',
 'islam.r.pf':'Fajr sunnah (2)','islam.r.bz':'Before Dhuhr (4)','islam.r.az':'After Dhuhr (2)',
 'islam.r.am':'After Maghrib (2)','islam.r.ai':'After Isha (2)',
 'islam.adhkar':'Morning & evening adhkar','islam.morning':'Morning','islam.evening':'Evening',
 'islam.m1':'Ayat Al-Kursi','islam.m2':'Al-Ikhlas & Al-Mu\'awwidhatayn (×3)','islam.m3':'Sayyid Al-Istighfar',
 'islam.m4':'Subhan Allah wa bihamdih (×100)','islam.m5':'Salawat upon the Prophet (×10)',
 'islam.m6':'La ilaha illa Allah wahdahu (×10)',
 'islam.e1':'Ayat Al-Kursi','islam.e2':'Al-Ikhlas & Al-Mu\'awwidhatayn (×3)','islam.e3':'Astaghfirullah (×100)',
 'islam.e4':'Subhan Allah wa bihamdih (×100)','islam.e5':'Salawat upon the Prophet (×10)',
 'islam.e6':'A\'udhu bikalimatillah (×3)',
 'islam.tasbih':'Digital tasbih','islam.t.sub':'Subhan Allah','islam.t.ham':'Alhamdulillah',
 'islam.t.akb':'Allahu Akbar','islam.t.ist':'Astaghfirullah','islam.t.saw':'Salawat',
 'islam.tasbihDone':'Completed {n} — tabarakallah','islam.tasbihTotal':'Lifetime total','islam.tasbihReset':'Reset count',
 'islam.quran':'Quran khatma','islam.qTarget':'Daily target','islam.qEta':'Estimated khatma',
 'islam.qDone':'Khatma complete — may Allah accept it','islam.qNew':'Start a new khatma',
 'islam.fasting':'Voluntary fasting','islam.fastToday':'Fasting today','islam.sunnahDay':'A recommended fast day',
 'islam.whiteDays':'The white days','islam.upcoming':'Upcoming fast days','islam.totalFasts':'recorded fasts',
 'islam.monday':'Monday','islam.thursday':'Thursday',
 'islam.ramadan':'Ramadan','islam.ramIn':'Ramadan begins in','islam.ramDays':'days',
 'islam.ramMubarak':'Ramadan Kareem','islam.ramLeft':'days of Ramadan remain',
},
ar:{
 'app.name':'أكاديمية عُبَدْ','hub.head':'ادخل إلى أكاديميتك',
 'hub.foot':'محلي · دون اتصال · خاص','nav.hub':'الرئيسية',
 'nav.dashboard':'لوحة التحكم','nav.courses':'المقررات','nav.notes':'الملاحظات','nav.calendar':'التقويم',
 'nav.islam':'أنا مسلم','nav.analytics':'التحليلات','nav.study':'أدوات الدراسة','nav.settings':'الإعدادات',
 'sub.dashboard':'يومك في لمحة','sub.courses':'رحلتك الأكاديمية','sub.notes':'ملاحظاتك الدراسية',
 'sub.calendar':'الجدول والفعاليات','sub.islam':'عبادتي اليومية','sub.analytics':'رؤى وإحصاءات',
 'sub.study':'بطاقات واختبارات','sub.settings':'خصّص تجربتك',
 'common.add':'إضافة','common.save':'حفظ','common.cancel':'إلغاء','common.delete':'حذف','common.edit':'تعديل',
 'common.close':'إغلاق','common.back':'رجوع','common.search':'بحث','common.optional':'اختياري',
 'common.today':'اليوم','common.tomorrow':'غدًا','common.confirmDelete':'حذف هذا العنصر؟ لا يمكن التراجع عن هذا الإجراء.',
 'common.done':'تم','toast.saved':'تم الحفظ','toast.deleted':'تم الحذف','toast.error':'حدث خطأ ما.',
 'dash.greeting':'مرحبًا، {name}','dash.stPrayers':'الصلوات','dash.stCourses':'المقررات','dash.stTasks':'مهام مفتوحة','dash.stNotes':'الملاحظات',
 'dash.tasks':'المهام','dash.addTaskPh':'أضف مهمة سريعة…','dash.newTask':'مهمة جديدة','dash.taskTitle':'عنوان المهمة',
 'dash.taskDue':'تاريخ الاستحقاق (اختياري)','dash.noTasks':'لا مهام مفتوحة — استمتع بالهدوء.',
 'dash.upcoming':'القادم','dash.noEvents':'لا فعاليات قادمة.','dash.recentNotes':'أحدث الملاحظات','dash.noNotes':'لا توجد ملاحظات بعد.',
 'dash.quick':'إجراءات سريعة','dash.newNote':'ملاحظة جديدة','dash.newEvent':'حدث جديد','dash.goStudy':'ابدأ الدراسة','dash.overdue':'متأخرة',
 'courses.new':'مقرر جديد','courses.edit':'تعديل المقرر','courses.name':'اسم المقرر','courses.code':'الرمز',
 'courses.instructor':'المحاضر','courses.credits':'الساعات','courses.cr':'ساع','courses.semester':'الفصل الدراسي',
 'courses.empty':'لا توجد مقررات بعد','courses.emptyHint':'أنشئ أول مقرر لتنظيم فصلك الدراسي.',
 'courses.lessonsLc':'دروس','courses.units':'الوحدات','courses.newUnit':'وحدة جديدة','courses.unitName':'عنوان الوحدة',
 'courses.lessons':'الدروس','courses.newLesson':'درس جديد','courses.lessonName':'عنوان الدرس',
 'courses.noUnits':'لا وحدات بعد. أضف وحدة لتنظيم هذا المقرر.','courses.noLessons':'لا دروس في هذه الوحدة بعد.',
 'courses.deleteUnit':'حذف الوحدة','courses.needName':'أدخل اسمًا للمقرر.',
 'notes.new':'ملاحظة جديدة','notes.searchPh':'ابحث في الملاحظات…','notes.empty':'لا توجد ملاحظات',
 'notes.emptyHint':'اكتب ملاحظتك الأولى — تبقى محفوظة على هذا الجهاز.',
 'notes.titlePh':'عنوان الملاحظة','notes.bodyPh':'ابدأ الكتابة…','notes.tagsPh':'وسوم، مفصولة بفواصل',
 'notes.attachImage':'إضافة صورة','notes.attachAudio':'إضافة صوت','notes.images':'الصور','notes.audio':'الصوت',
 'notes.deleteMsg':'حذف هذه الملاحظة نهائيًا؟','notes.untitled':'ملاحظة بدون عنوان',
 'notes.discard':'تجاهل التغييرات؟','notes.discardMsg':'لديك تغييرات غير محفوظة في هذه الملاحظة.',
 'notes.discardBtn':'تجاهل','notes.tooBig':'حجم الملف أكبر من 5 ميغابايت.','notes.badType':'نوع ملف غير مدعوم.',
 'cal.new':'حدث جديد','cal.edit':'تعديل الحدث','cal.eventTitle':'عنوان الحدث','cal.desc':'الوصف',
 'cal.time':'الوقت','cal.date':'التاريخ','cal.none':'لا فعاليات في هذا اليوم.','cal.deleteMsg':'حذف هذا الحدث؟',
 'cal.needTitle':'العنوان والتاريخ مطلوبان.','cal.prev':'الشهر السابق','cal.next':'الشهر التالي',
 'ana.gpaTrend':'تطور المعدل','ana.courseProgress':'تقدم المقررات','ana.tasksDonut':'إنجاز المهام',
 'ana.notesActivity':'نشاط الملاحظات','ana.noData':'البيانات غير كافية بعد.','ana.done':'منجزة','ana.pending':'قيد الانتظار',
 'ana.stat.notes':'الملاحظات','ana.stat.cards':'البطاقات','ana.stat.prayers':'صلوات اليوم','ana.stat.quran':'تقدم الختمة',
 'ana.prayersTrend':'انتظام الصلاة (7 أيام)',
 'study.tabCards':'البطاقات','study.tabQuiz':'الاختبارات','study.newDeck':'مجموعة جديدة','study.deckName':'عنوان المجموعة',
 'study.noDecks':'لا مجموعات بعد.','study.noDecksHint':'أنشئ مجموعة وأضف إليها بطاقات المراجعة.',
 'study.cardsLc':'بطاقة','study.study':'دراسة','study.newCard':'إضافة بطاقة','study.front':'الوجه الأمامي','study.back':'الوجه الخلفي',
 'study.frontPh':'السؤال / التلميح','study.backPh':'الإجابة','study.flip':'اقلب','study.shuffle':'خلط',
 'study.prev':'السابق','study.next':'التالي','study.deleteDeck':'حذف المجموعة',
 'study.deleteDeckMsg':'حذف هذه المجموعة وكل بطاقاتها؟','study.editCard':'تعديل البطاقة',
 'study.noCards':'لا بطاقات في هذه المجموعة بعد.','study.addFirst':'أضف أول بطاقة لتبدأ الدراسة.',
 'study.newQuiz':'اختبار جديد','study.editQuiz':'تعديل الاختبار','study.quizName':'عنوان الاختبار','study.noQuizzes':'لا اختبارات بعد.',
 'study.noQuizzesHint':'أنشئ اختبارًا بأسئلتك وإجاباتك الخاصة.',
 'study.question':'سؤال','study.questionPh':'اكتب السؤال…','study.option':'الخيار {n}',
 'study.correctOpt':'تحديد كإجابة صحيحة','study.addQuestion':'إضافة سؤال','study.removeQ':'حذف السؤال',
 'study.start':'ابدأ','study.qOf':'السؤال {i} من {n}','study.nextQ':'التالي','study.finish':'إنهاء',
 'study.selectFirst':'اختر إجابة أولًا.','study.score':'نتيجتك','study.review':'مراجعة',
 'study.your':'إجابتك','study.correctAns':'الصحيحة','study.retry':'إعادة','study.questionsLc':'أسئلة',
 'study.needTitle':'أعطِ الاختبار عنوانًا.','study.minQ':'يحتاج الاختبار إلى سؤال واحد على الأقل.',
 'study.needQ':'السؤال {n} يحتاج نصًا وخيارين على الأقل وتحديد الإجابة الصحيحة.',
 'set.language':'اللغة','set.langDesc':'لغة الواجهة واتجاهها (من اليمين لليسار / من اليسار لليمين).',
 'set.profile':'الملف الشخصي','set.username':'اسم المستخدم','set.usernamePh':'اسمك',
 'set.usernameDesc':'يُستخدم فقط في التحية على لوحة التحكم — لا يعيد تسمية بياناتك أبدًا.',
 'set.appearance':'المظهر','set.theme':'السمة','set.dark':'داكنة','set.light':'فاتحة',
 'set.th.dark':'منتصف الليل','set.th.oled':'أسود نقي','set.th.light':'الشفق',
 'set.th.paper':'ورقي','set.th.sage':'نعناعي','set.th.rose':'وردي',
 'set.bg':'خلفيات الثيمات','set.bgDesc':'ارفع خلفية خاصة لكل ثيم (حتى 5 ميغابايت). تُحفظ على جهازك دون اتصال وتظهر بشفافية خلف الواجهة.',
 'set.bgUp':'رفع','set.bgRm':'إزالة','set.bgApplied':'تم تحديث الخلفية.','set.bgRemoved':'تمت إزالة الخلفية.',
 'set.sound':'أصوات الواجهة','set.soundDesc':'أصوات تفاعل خفيفة..',
 'set.backup':'النسخ الاحتياطي والاستعادة','set.backupDesc':'صدّر جميع بياناتك كملف JSON واستعدها على أي جهاز. بدون حساب.',
 'set.export':'تصدير نسخة احتياطية','set.import':'استيراد نسخة احتياطية',
 'set.importConfirm':'الاستيراد سيستبدل جميع البيانات الحالية على هذا الجهاز. هل تريد المتابعة؟',
 'set.imported':'تمت الاستعادة بنجاح.','set.importFailed':'ملف نسخة احتياطية غير صالح.',
 'set.danger':'منطقة الخطر',
 'set.clearMsg':'سيحذف هذا نهائيًا كل المقررات والملاحظات والفعاليات وسجلات العبادة ومحتوى الدراسة المحفوظة على هذا الجهاز.',
 'set.clearAll':'محو جميع البيانات','set.cleared':'تم محو جميع البيانات.',
 'set.about':'حول','set.aboutBody':'بيئة أكاديمية محلية بالكامل. بلا حساب ولا خادم — بياناتك تبقى معك.',
 'set.version':'الإصدار','set.nameSaved':'تم تحديث الاسم.','set.needName':'أدخل اسمًا من فضلك.',
 'set.exported':'تم تنزيل ملف النسخة الاحتياطية.',
 'search.ph':'ابحث في الملاحظات والمقررات والفعاليات…','search.none':'لا نتائج لـ «{q}»',
 'search.notes':'الملاحظات','search.courses':'المقررات','search.events':'الفعاليات','search.decks':'البطاقات','search.quizzes':'الاختبارات',
 'dash.greet.morning':'صباح الخير، {name}','dash.greet.afternoon':'مساء الخير، {name}',
 'dash.greet.evening':'مساء الخير، {name}','dash.greet.night':'طابت ليلتك، {name}',
 'focus.tab':'التركيز','focus.session':'جلسة تركيز','focus.break':'راحة',
 'focus.start':'ابدأ','focus.pause':'إيقاف مؤقت','focus.reset':'إعادة',
 'focus.today':'جلسات اليوم','focus.length':'مدة الجلسة','focus.min':'دقيقة',
 'focus.doneMsg':'انتهت جلسة التركيز — خذ قسطًا من الراحة','focus.breakOver':'انتهت الراحة — جاهز لجلسة أخرى؟',
 'notes.pin':'تثبيت الملاحظة','notes.unpin':'إلغاء التثبيت','notes.pinned':'مثبتة',
 'lb.open':'عرض الصورة','lb.zoomIn':'تكبير','lb.zoomOut':'تصغير','lb.reset':'الحجم الأصلي',
 'islam.peace':'السلام عليكم ورحمة الله وبركاته',
 'islam.prayers':'الصلوات الخمس','islam.p.fajr':'الفجر','islam.p.zuhr':'الظهر','islam.p.asr':'العصر',
 'islam.p.maghrib':'المغرب','islam.p.isha':'العشاء','islam.jamaah':'جماعة',
 'islam.prayersDone5':'أتممت الصلوات الخمس — تقبل الله',
 'islam.rawatib':'السنن الرواتب','islam.rak':'ركعة',
 'islam.r.pf':'راتبة الفجر (2)','islam.r.bz':'قبل الظهر (4)','islam.r.az':'بعد الظهر (2)',
 'islam.r.am':'بعد المغرب (2)','islam.r.ai':'بعد العشاء (2)',
 'islam.adhkar':'أذكار الصباح والمساء','islam.morning':'الصباح','islam.evening':'المساء',
 'islam.m1':'آية الكرسي','islam.m2':'الإخلاص والمعوذتان (×3)','islam.m3':'سيد الاستغفار',
 'islam.m4':'سبحان الله وبحمده (×100)','islam.m5':'الصلاة على النبي ﷺ (×10)',
 'islam.m6':'لا إله إلا الله وحده لا شريك له (×10)',
 'islam.e1':'آية الكرسي','islam.e2':'الإخلاص والمعوذتان (×3)','islam.e3':'أستغفر الله وأتوب إليه (×100)',
 'islam.e4':'سبحان الله وبحمده (×100)','islam.e5':'الصلاة على النبي ﷺ (×10)',
 'islam.e6':'أعوذ بكلمات الله التامات (×3)',
 'islam.tasbih':'المسبحة الإلكترونية','islam.t.sub':'سبحان الله','islam.t.ham':'الحمد لله',
 'islam.t.akb':'الله أكبر','islam.t.ist':'أستغفر الله','islam.t.saw':'صلى الله عليه وسلم',
 'islam.tasbihDone':'أتممت {n} — تقبل الله','islam.tasbihTotal':'الإجمالي الكلي','islam.tasbihReset':'تصفير',
 'islam.quran':'ختمة القرآن','islam.qTarget':'الهدف اليومي','islam.qEta':'الختمة المتوقعة',
 'islam.qDone':'تمت الختمة — تقبل الله','islam.qNew':'ابدأ ختمة جديدة',
 'islam.fasting':'صيام التطوع','islam.fastToday':'صيام اليوم','islam.sunnahDay':'من أيام الصيام المستحبة',
 'islam.whiteDays':'الأيام البيض','islam.upcoming':'أيام الصيام القادمة','islam.totalFasts':'يومًا مسجلًا',
 'islam.monday':'الاثنين','islam.thursday':'الخميس',
 'islam.ramadan':'رمضان','islam.ramIn':'باقي على رمضان','islam.ramDays':'يوم',
 'islam.ramMubarak':'رمضان كريم','islam.ramLeft':'يومًا متبقيًا من رمضان',
}};
const t=(k,vars)=>{ let s=(I18N[state.settings.lang]||I18N.en)[k]; if(s==null) s=I18N.en[k]||k;
  if(vars) for(const key in vars) s=s.split('{'+key+'}').join(vars[key]); return s; };

/* ═══ 2.5 islam tables (قبل الحالة حتى تُستخدم عند التهيئة) ══ */
const PRAYER_KEYS=['fajr','zuhr','asr','maghrib','isha'];
const RAWATIB_KEYS=['pf','bz','az','am','ai'];
const RAWATIB_RAKA={pf:2,bz:4,az:2,am:2,ai:2};
const MORNING_ADHKAR=['m1','m2','m3','m4','m5','m6'];
const EVENING_ADHKAR=['e1','e2','e3','e4','e5','e6'];
const TASBIH_MODES=['sub','ham','akb','ist','saw'];
const TASBIH_TARGETS=[33,100,1000];
function normIslam(x){ x=(x&&typeof x==='object')?x:{};
  const day=/^\d{4}-\d{2}-\d{2}$/.test(String(x.day||''))?String(x.day):'';
  const P=v=>clampNum(v,0,2,0), R=v=>clampNum(v,0,1,0);
  const ids=a=>{ const o={}; normArr(a).forEach(k=>{ if(typeof k==='string'&&k.length<6) o[k]=true; }); return o; };
  const hist={}; if(x.hist&&typeof x.hist==='object')
    Object.keys(x.hist).forEach(k=>{ if(/^\d{4}-\d{2}-\d{2}$/.test(k)) hist[k]=clampNum(x.hist[k],0,5,0); });
  return { day,
    prayers:{ fajr:P(x.prayers&&x.prayers.fajr), zuhr:P(x.prayers&&x.prayers.zuhr),
      asr:P(x.prayers&&x.prayers.asr), maghrib:P(x.prayers&&x.prayers.maghrib), isha:P(x.prayers&&x.prayers.isha) },
    rawatib:{ pf:R(x.rawatib&&x.rawatib.pf), bz:R(x.rawatib&&x.rawatib.bz), az:R(x.rawatib&&x.rawatib.az),
      am:R(x.rawatib&&x.rawatib.am), ai:R(x.rawatib&&x.rawatib.ai) },
    morning:ids(x.morning), evening:ids(x.evening),
    tasbih:{ mode:TASBIH_MODES.includes(x.tasbih&&x.tasbih.mode)?x.tasbih.mode:'sub',
      count:clampNum(x.tasbih&&x.tasbih.count,0,1e6,0),
      total:clampNum(x.tasbih&&x.tasbih.total,0,1e9,0),
      target:TASBIH_TARGETS.includes(x.tasbih&&x.tasbih.target)?x.tasbih.target:33 },
    quran:{ juz:clampNum(x.quran&&x.quran.juz,0,30,0),
      target:clampNum(x.quran&&x.quran.target,1,10,1),
      started:clampNum(x.quran&&x.quran.started,0,1e15,Date.now()) },
    fasts:normArr(x.fasts).filter(d=>typeof d==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(d)).slice(0,1000),
    hist }; }

/* ═══ 3. state ═══════════════════════════════════════════════ */
const state={
  user:{name:'Ubad'},
  settings:{lang:'en',sound:true,theme:'dark'},
  courses:[], notes:[], events:[], tasks:[], decks:[], quizzes:[],
  focus:{day:'',done:0},
  islam:normIslam({})
};
const PREF_KEY='ubad.prefs.v1';
function savePrefs(){ try{ localStorage.setItem(PREF_KEY,JSON.stringify({
  name:state.user.name, lang:state.settings.lang, sound:state.settings.sound, theme:state.settings.theme})); }catch(e){} }
function loadPrefs(){ try{ const p=JSON.parse(localStorage.getItem(PREF_KEY)||'{}');
  if(p.name) state.user.name=normStr(p.name,40,'Ubad');
  if(p.lang==='ar'||p.lang==='en') state.settings.lang=p.lang;
  if(typeof p.sound==='boolean') state.settings.sound=p.sound;
  if(THEMES.includes(p.theme)) state.settings.theme=p.theme; }catch(e){} }

/* ═══ 4. storage — IndexedDB (graceful memory fallback) ══════ */
const DB={ db:null,
  open(){ return new Promise((res,rej)=>{
    if(!('indexedDB' in window)) return rej();
    const rq=indexedDB.open('ubad-academy-hub',1);
    rq.onupgradeneeded=()=>{ const d=rq.result;
      if(!d.objectStoreNames.contains('kv')) d.createObjectStore('kv',{keyPath:'id'});
      if(!d.objectStoreNames.contains('notes')) d.createObjectStore('notes',{keyPath:'id'}); };
    rq.onsuccess=()=>{ this.db=rq.result; res(); };
    rq.onerror=()=>rej(rq.error); }); },
  st(mode,store){ return this.db.transaction(store,mode).objectStore(store); },
  put(store,val){ if(!this.db) return Promise.resolve();
    return new Promise((res,rej)=>{ const r=this.st('readwrite',store).put(val); r.onsuccess=res; r.onerror=()=>rej(r.error); }); },
  get(store,key){ if(!this.db) return Promise.resolve(undefined);
    return new Promise((res,rej)=>{ const r=this.st('readonly',store).get(key); r.onsuccess=()=>res(r.result); r.onerror=()=>rej(r.error); }); },
  all(store){ if(!this.db) return Promise.resolve([]);
    return new Promise((res,rej)=>{ const r=this.st('readonly',store).getAll(); r.onsuccess=()=>res(r.result||[]); r.onerror=()=>rej(r.error); }); },
  del(store,key){ if(!this.db) return Promise.resolve();
    return new Promise((res,rej)=>{ const r=this.st('readwrite',store).delete(key); r.onsuccess=res; r.onerror=()=>rej(r.error); }); },
  clear(store){ if(!this.db) return Promise.resolve();
    return new Promise((res,rej)=>{ const r=this.st('readwrite',store).clear(); r.onsuccess=res; r.onerror=()=>rej(r.error); }); }
};

/* ── normalization (defensive against malformed stored data) ── */
const normCourse=c=>{ if(!c||typeof c!=='object') return null;
  return { id:normStr(c.id,40)||uid(), name:normStr(c.name,80,'Course'), code:normStr(c.code,24),
    instructor:normStr(c.instructor,80), credits:clampNum(c.credits,0,99,3), semester:normStr(c.semester,40),
    createdAt:clampNum(c.createdAt,0,1e15,Date.now()),
    units:normArr(c.units).map(u=>({ id:normStr(u&&u.id,40)||uid(), title:normStr(u&&u.title,80,'Unit'),
      lessons:normArr(u&&u.lessons).map(l=>({ id:normStr(l&&l.id,40)||uid(), title:normStr(l&&l.title,120,'Lesson'), done:!!(l&&l.done) })) })) }; };
function hydrate(d){ if(!d||typeof d!=='object') return;
  state.user.name=normStr(d.user&&d.user.name,40,state.user.name);
  const s=d.settings||{};
  if(s.lang==='ar'||s.lang==='en') state.settings.lang=s.lang;
  if(typeof s.sound==='boolean') state.settings.sound=s.sound;
  if(THEMES.includes(s.theme)) state.settings.theme=s.theme;
  state.focus=(d.focus&&typeof d.focus==='object')?
    { day:normStr(d.focus.day,10), done:clampNum(d.focus.done,0,999,0) }:{ day:'',done:0 };
  state.islam=normIslam(d.islam);
  state.courses=normArr(d.courses).map(normCourse).filter(Boolean);
  state.events=normArr(d.events).map(e=>({ id:normStr(e&&e.id,40)||uid(), title:normStr(e&&e.title,120,'Event'),
    desc:normStr(e&&e.desc,500), date:/^\d{4}-\d{2}-\d{2}$/.test((e&&e.date)||'')?e.date:today(),
    time:/^\d{2}:\d{2}$/.test((e&&e.time)||'')?e.time:'', createdAt:clampNum(e&&e.createdAt,0,1e15,Date.now()) }));
  state.tasks=normArr(d.tasks).map(x=>({ id:normStr(x&&x.id,40)||uid(), title:normStr(x&&x.title,120,'Task'),
    done:!!(x&&x.done), due:/^\d{4}-\d{2}-\d{2}$/.test((x&&x.due)||'')?x.due:'', createdAt:clampNum(x&&x.createdAt,0,1e15,Date.now()) }));
  state.decks=normArr(d.decks).map(k=>({ id:normStr(k&&k.id,40)||uid(), title:normStr(k&&k.title,80,'Deck'),
    createdAt:clampNum(k&&k.createdAt,0,1e15,Date.now()),
    cards:normArr(k&&k.cards).map(c=>({ id:normStr(c&&c.id,40)||uid(), front:normStr(c&&c.front,300), back:normStr(c&&c.back,300) })) }));
  state.quizzes=normArr(d.quizzes).map(z=>({ id:normStr(z&&z.id,40)||uid(), title:normStr(z&&z.title,80,'Quiz'),
    createdAt:clampNum(z&&z.createdAt,0,1e15,Date.now()),
    questions:normArr(z&&z.questions).map(q=>({ q:normStr(q&&q.q,400),
      options:normArr(q&&q.options).slice(0,4).map(o=>normStr(o,160)), correct:clampNum(q&&q.correct,0,3,0) }))
      .filter(q=>q.q&&q.options.filter(Boolean).length>=2&&q.options[q.correct]) }));
}
async function loadData(){ try{ const rec=await DB.get('kv','appdata'); if(rec&&rec.data) hydrate(rec.data); }catch(e){} }
async function loadNotes(){ try{
  const arr=await DB.all('notes');
  state.notes=arr.filter(n=>n&&n.id).map(n=>({ id:n.id, title:normStr(n.title,120), body:normStr(n.body,20000),
    tags:normArr(n.tags).map(x=>normStr(x,24)).slice(0,8),
    createdAt:clampNum(n.createdAt,0,1e15,Date.now()), updatedAt:clampNum(n.updatedAt,0,1e15,Date.now()), pin:!!n.pin,
    images:normArr(n.images).filter(a=>a&&a.blob instanceof Blob).map(a=>({name:normStr(a.name,80,'image'),blob:a.blob})),
    audio:normArr(n.audio).filter(a=>a&&a.blob instanceof Blob).map(a=>({name:normStr(a.name,80,'audio'),blob:a.blob})) }))
    .sort((a,b)=>(b.pin?1:0)-(a.pin?1:0)||b.updatedAt-a.updatedAt);
}catch(e){ state.notes=state.notes||[]; } }
async function saveNote(rec){ try{ await DB.put('notes',rec); }catch(e){}
  const i=state.notes.findIndex(n=>n.id===rec.id);
  if(i>-1) state.notes[i]=rec; else state.notes.unshift(rec);
  state.notes.sort((a,b)=>(b.pin?1:0)-(a.pin?1:0)||b.updatedAt-a.updatedAt); }
function saveData(){ Nav.invalidate('dashboard','analytics'); savePrefs();
  DB.put('kv',{id:'appdata',data:{user:state.user,settings:state.settings,courses:state.courses,
    events:state.events,tasks:state.tasks,decks:state.decks,quizzes:state.quizzes,
    focus:state.focus,islam:state.islam}}).catch(()=>{}); }

/* ═══ 5. audio manager — fails silently, never blocks ════════ */
const Sound={ files:{click:'assets/audio/click.mp3',move:'assets/audio/3d-move.mp3',
    back:'assets/audio/back.mp3',transition:'assets/audio/transition.mp3'},
  els:{}, stat:{}, unlocked:false, ctx:null,
  init(){ for(const k in this.files){ try{
      const a=new Audio(); a.preload='auto'; a.src=this.files[k];
      a.addEventListener('error',()=>{this.stat[k]='missing';},{once:true});
      a.addEventListener('canplaythrough',()=>{this.stat[k]='ok';},{once:true});
      this.els[k]=a; }catch(e){ this.stat[k]='missing'; } }
    const unlock=()=>{ this.unlocked=true;
      window.removeEventListener('pointerdown',unlock); window.removeEventListener('keydown',unlock); };
    window.addEventListener('pointerdown',unlock); window.addEventListener('keydown',unlock); },
  play(k){ if(!state.settings.sound) return;
    try{ const el=this.els[k];
      if(!el||this.stat[k]==='missing'){ this.blip(k); return; }
      el.currentTime=0; el.volume=(k==='click')?.35:.5;
      const p=el.play(); if(p&&p.catch) p.catch(()=>{});
    }catch(e){} },
  blip(k){ /* tiny synthesized fallback ONLY when the real files are missing */
    if(!state.settings.sound) return;
    try{ this.ctx=this.ctx||new (window.AudioContext||window.webkitAudioContext)();
      if(this.ctx.state==='suspended') this.ctx.resume();
      const t0=this.ctx.currentTime, o=this.ctx.createOscillator(), g=this.ctx.createGain();
      const f=(k==='back')?230:(k==='move')?330:(k==='transition')?450:540;
      o.type='sine'; o.frequency.setValueAtTime(f,t0);
      o.frequency.exponentialRampToValueAtTime(f*.72,t0+.09);
      g.gain.setValueAtTime(.0001,t0);
      g.gain.exponentialRampToValueAtTime(.045,t0+.012);
      g.gain.exponentialRampToValueAtTime(.0001,t0+.13);
      o.connect(g); g.connect(this.ctx.destination); o.start(t0); o.stop(t0+.15);
    }catch(e){} }
};

/* ═══ 5.7 history bridge (native back) ══════════════════════ */
const Hist={
  ready:false,
  depth(){ return Nav.stack.length-1; },
  init(){ try{ history.replaceState({d:0},''); }catch(e){}
    this.ready=true;
    window.addEventListener('popstate',e=>this.onPop(e)); },
  pushDepth(){ try{ history.pushState({d:this.depth()},''); }catch(e){} },
  sync(){ try{ history.replaceState({d:this.depth()},''); }catch(e){} },
  onPop(e){
    if(!this.ready) return;
    const d=(e.state&&typeof e.state.d==='number')?e.state.d:0;
    const cur=this.depth();
    if(d<cur) Nav._histBack(cur-d);
    else if(d>cur) this.sync();
  }
};

/* ═══ 5.8 FX — جزيئات الهب + الاحتفال ═══════════════════════ */
const FX={
  sRaf:0,
  weak:(navigator.hardwareConcurrency||4)<=2||((navigator.deviceMemory||4)<=2),
  hubGate(nav){ const top=nav.stack[nav.stack.length-1];
    if(top&&top.id==='hub'&&!this.weak&&!RM.matches) this.starsStart();
    else this.starsStop(); },
  starsStart(){
    const layer=document.querySelector('.layer[data-layer="hub"]');
    const cv=layer&&layer.querySelector('.hub-stars'); if(!cv) return;
    this.starsStop();
    const w=layer.clientWidth||innerWidth, h=layer.clientHeight||innerHeight;
    const dpr=Math.min(1.5,devicePixelRatio||1);
    cv.width=w*dpr; cv.height=h*dpr;
    const x=cv.getContext('2d'); x.setTransform(dpr,0,0,dpr,0,0);
    const th=document.documentElement.dataset.theme;
    const light=th!=='dark'&&th!=='oled';
    const cols=['34,211,238','59,130,246','139,92,246'];
    const ps=Array.from({length:26},()=>({ x:Math.random()*w, y:Math.random()*h,
      vy:-(.05+Math.random()*.11), r:.8+Math.random()*1.4,
      ph:Math.random()*6.28, sp:.4+Math.random()*.8, c:cols[Math.random()*3|0] }));
    let t=0; const AM=light?.32:.8;
    const loop=()=>{ t+=.016; x.clearRect(0,0,w,h);
      ps.forEach(p=>{ p.y+=p.vy; if(p.y<-4){ p.y=h+4; p.x=Math.random()*w; }
        const a=AM*(.35+.65*(Math.sin(t*p.sp+p.ph)*.5+.5));
        x.beginPath(); x.arc(p.x,p.y,p.r,0,6.283);
        x.fillStyle=`rgba(${p.c},${a.toFixed(3)})`; x.fill(); });
      this.sRaf=requestAnimationFrame(loop); };
    this.sRaf=requestAnimationFrame(loop);
    if(!this._rs){ this._rs=true;
      window.addEventListener('resize',()=>{ if(this.sRaf) this.starsStart(); });
      document.addEventListener('visibilitychange',()=>{
        if(document.hidden){ this.starsStop(); } else this.hubGate(Nav); }); }
  },
  starsStop(){ if(this.sRaf){ cancelAnimationFrame(this.sRaf); this.sRaf=0; } },
  confetti(){
    if(RM.matches||this.weak) return;
    try{
      const cv=document.createElement('canvas');
      const w=innerWidth,h=innerHeight,dpr=Math.min(2,devicePixelRatio||1);
      cv.width=w*dpr; cv.height=h*dpr;
      cv.style.cssText='position:fixed;inset:0;z-index:600;pointer-events:none';
      const x=cv.getContext('2d'); x.setTransform(dpr,0,0,dpr,0,0);
      $('#overlay-root').appendChild(cv);
      const cols=['#22D3EE','#3B82F6','#8B5CF6','#34D399'];
      const ps=Array.from({length:70},()=>({ x:w/2+(Math.random()-.5)*90, y:h*.32,
        vx:(Math.random()-.5)*9, vy:-(4+Math.random()*7), g:.18+Math.random()*.08,
        r:2+Math.random()*3, c:cols[Math.random()*cols.length|0],
        rot:Math.random()*6, vr:(Math.random()-.5)*.3 }));
      const t0=performance.now();
      const step=now=>{ const el=now-t0; x.clearRect(0,0,w,h);
        ps.forEach(p=>{ p.x+=p.vx; p.y+=p.vy; p.vy+=p.g; p.rot+=p.vr;
          x.save(); x.globalAlpha=Math.max(0,1-el/1100);
          x.translate(p.x,p.y); x.rotate(p.rot); x.fillStyle=p.c;
          x.fillRect(-p.r,-p.r*.6,p.r*2,p.r*1.2); x.restore(); });
        if(el<1150) requestAnimationFrame(step); else cv.remove(); };
      requestAnimationFrame(step);
    }catch(e){}
  }
};

/* ═══ 5.9 مؤقت التركيز ══════════════════════════════════════ */
const Focus={
  phase:'focus', mins:25, breakMins:5, remaining:25*60, running:false, endsAt:0, iv:0,
  today(){ return ymd(new Date()); },
  done(){ if(state.focus.day!==this.today()){ state.focus.day=this.today(); state.focus.done=0; }
    return state.focus.done||0; },
  start(rr){ this.running=true; this.endsAt=Date.now()+this.remaining*1000;
    clearInterval(this.iv); this.iv=setInterval(()=>this.tick(rr),250); this.tick(rr); },
  pause(){ this.running=false; clearInterval(this.iv);
    this.remaining=Math.max(0,Math.round((this.endsAt-Date.now())/1000)); },
  reset(){ this.running=false; clearInterval(this.iv);
    this.remaining=(this.phase==='focus'?this.mins:this.breakMins)*60; },
  setPhase(p){ this.phase=p; this.reset(); },
  tick(rr){ const left=this.running?
      Math.max(0,Math.round((this.endsAt-Date.now())/1000)):this.remaining;
    this.remaining=left;
    if(left<=0){ this.running=false; clearInterval(this.iv);
      if(this.phase==='focus'){
        state.focus.day=this.today(); state.focus.done=(state.focus.done||0)+1;
        saveData(); Sound.play('transition'); toast(t('focus.doneMsg')); FX.confetti();
        this.setPhase('break');
      } else { Sound.play('transition'); toast(t('focus.breakOver')); this.setPhase('focus'); }
      return; }
    if(rr) rr(); }
};

/* ── مساعدات صغيرة ── */
function pulse(el){ if(!el||RM.matches) return;
  el.classList.remove('pulse-once'); void el.offsetWidth; el.classList.add('pulse-once'); }
function fmtMMSS(s){ return String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0'); }
function greetKey(){ const h=new Date().getHours();
  return h>=5&&h<12?'morning':h>=12&&h<17?'afternoon':h>=17&&h<22?'evening':'night'; }
function bindEdgeBack(){ /* سحب من حافة البداية = رجوع (للمتصفحات بلا إيماءة أصلية) */
  if(!matchMedia('(pointer:coarse)').matches) return;
  let x0=0,y0=0,armed=false; const EDGE=26,DIST=72;
  window.addEventListener('touchstart',e=>{
    armed=false;
    if(Nav.busy||Nav.stack.length<=1||searchOpen||activeModal) return;
    const t=e.touches[0],W=innerWidth;
    const rtl=document.documentElement.dir==='rtl';
    const nearStart=rtl?(W-t.clientX)<EDGE:t.clientX<EDGE;
    if(!nearStart) return;
    armed=true; x0=t.clientX; y0=t.clientY;
  },{passive:true});
  window.addEventListener('touchmove',e=>{
    if(!armed) return; const t=e.touches[0];
    if(Math.abs(t.clientY-y0)>Math.abs(t.clientX-x0)*1.2) armed=false;
  },{passive:true});
  window.addEventListener('touchend',e=>{
    if(!armed) return; armed=false;
    const dx=e.changedTouches[0].clientX-x0;
    const back=document.documentElement.dir==='rtl'?dx<-DIST:dx>DIST;
    if(back) Nav.back();
  });
  window.addEventListener('pointercancel',()=>{ armed=false; });
}
function bindKeys(){ /* Ctrl/Cmd + K للبحث */
  window.addEventListener('keydown',e=>{
    if((e.ctrlKey||e.metaKey)&&String(e.key).toLowerCase()==='k'){
      if(activeModal||searchOpen) return;
      e.preventDefault(); openSearch(); } });
}

/* ═══ 6. toast + modal ═══════════════════════════════════════ */
function toast(msg,kind){ const root=$('#toast-root'); const el=document.createElement('div');
  el.className='toast'+(kind==='err'?' err':'');
  el.innerHTML=`${ic(kind==='err'?'alert':'check','ic-s')}<span>${esc(msg)}</span>`;
  root.appendChild(el);
  setTimeout(()=>{ el.classList.add('out'); setTimeout(()=>el.remove(),320); },2600); }

let activeModal=null,lastFocus=null;
function openModal(opt){
  if(activeModal) activeModal.close(true);
  const root=document.createElement('div'); root.className='mback';
  root.innerHTML=`<div class="modal${opt.wide?' modal-wide':''}" role="dialog" aria-modal="true">
    <header class="mhead"><h2>${esc(opt.title)}</h2>
      <button class="icon-btn m-x" aria-label="${t('common.close')}">${ic('x')}</button></header>
    <div class="mbody">${opt.body||''}</div><footer class="mfoot"></footer></div>`;
  const foot=$('.mfoot',root);
  const api={ root, close(instant){
    if(!root.isConnected) return;
    if(instant){ root.remove(); if(activeModal===api) activeModal=null; if(lastFocus&&lastFocus.focus)lastFocus.focus(); return; }
    root.classList.add('closing');
    setTimeout(()=>{ root.remove(); if(activeModal===api) activeModal=null;
      if(lastFocus&&lastFocus.focus) lastFocus.focus(); },170); } };
  (opt.actions||[]).forEach(a=>{ const b=document.createElement('button');
    b.className='btn '+(a.cls||''); b.textContent=a.label;
    b.addEventListener('click',()=>{ if(a.cls&&a.cls.includes('btn-primary')) pulse(b);
      a.onClick?a.onClick(()=>api.close()):api.close(); });
    foot.appendChild(b); });
  if(!(opt.actions||[]).length) foot.style.display='none';
  $('.m-x',root).addEventListener('click',()=>api.close());
  root.addEventListener('pointerdown',e=>{ if(e.target===root) api.close(); });
  root.addEventListener('keydown',e=>{ /* focus trap + escape */
    if(e.key==='Escape'){ e.stopPropagation(); api.close(); }
    if(e.key==='Tab'){ const fo=$$('button,input,select,textarea,audio,[tabindex]',root)
        .filter(x=>!x.disabled&&x.offsetParent!==null);
      if(!fo.length) return; const first=fo[0],last=fo[fo.length-1];
      if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}
      else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();} } });
  $('#overlay-root').appendChild(root);
  activeModal=api; lastFocus=document.activeElement;
  requestAnimationFrame(()=>{ root.classList.add('open');
    const fi=$('input,select,textarea',root); (fi||$('.m-x',root)).focus(); });
  return api;
}
function confirmModal(opt){ openModal({ title:opt.title,
  body:`<p class="m-msg">${esc(opt.msg)}</p>`,
  actions:[{label:t('common.cancel')},
    {label:opt.okLabel||t('common.delete'),cls:'btn-danger',
     onClick:close=>{close(); if(opt.onOk)opt.onOk();}}]}); }
const field=(label,inner,hint)=>`<label class="field"><span class="f-label">${label}</span>${inner}${hint?`<span class="f-hint">${hint}</span>`:''}</label>`;
const inp=(id,ph,val,type)=>`<input class="input" id="${id}" type="${type||'text'}" placeholder="${esc(ph||'')}" value="${esc(val==null?'':val)}">`;

/* ═══ 6.5 image lightbox — عارض صور الملاحظات (تكبير/تصغير/سحب) ═ */
function openLightbox(src,name){
  if(activeModal) activeModal.close(true);
  const wrap=document.createElement('div'); wrap.className='lb-back';
  wrap.setAttribute('role','dialog'); wrap.setAttribute('aria-modal','true');
  wrap.setAttribute('aria-label',t('lb.open'));
  wrap.innerHTML=`
    <div class="lb-bar">
      <span class="lb-name mono">${esc(name||'')}</span>
      <div class="lb-tools">
        <span class="lb-zoom mono" id="lb-zoom">100%</span>
        <button class="icon-btn" id="lb-out" aria-label="${t('lb.zoomOut')}" title="${t('lb.zoomOut')}">−</button>
        <button class="icon-btn" id="lb-in" aria-label="${t('lb.zoomIn')}" title="${t('lb.zoomIn')}">+</button>
        <button class="icon-btn" id="lb-fit" aria-label="${t('lb.reset')}" title="${t('lb.reset')}">${ic('refresh')}</button>
        <button class="icon-btn" id="lb-x" aria-label="${t('common.close')}" title="${t('common.close')}">${ic('x')}</button>
      </div>
    </div>
    <div class="lb-stage" id="lb-stage">
      <img class="lb-img" id="lb-img" src="${src}" alt="${esc(name||'')}" draggable="false">
    </div>`;
  $('#overlay-root').appendChild(wrap);
  const img=$('#lb-img',wrap), stage=$('#lb-stage',wrap), zl=$('#lb-zoom',wrap);
  let scale=1,tx=0,ty=0,dragging=false,moved=false,downOnImg=false,sx=0,sy=0,pinchD=0,closed=false;
  let lastTapT=0,lastTapX=0,lastTapY=0,lastTouchZoom=0;
  const MIN=1,MAX=6;
  const apply=()=>{ img.style.transform=`translate(${tx}px,${ty}px) scale(${scale})`;
    img.classList.toggle('zoomed',scale>MIN);
    if(zl) zl.textContent=Math.round(scale*100)+'%'; };
  const clampPan=()=>{ const r=stage.getBoundingClientRect();
    const maxX=Math.max(0,(img.offsetWidth*scale-r.width)/2);
    const maxY=Math.max(0,(img.offsetHeight*scale-r.height)/2);
    tx=Math.min(maxX,Math.max(-maxX,tx));
    ty=Math.min(maxY,Math.max(-maxY,ty)); };
  const zoom=(f,cx,cy)=>{ const old=scale;
    scale=Math.min(MAX,Math.max(MIN,scale*f));
    if(scale===old) return;
    if(cx!=null){ const r=stage.getBoundingClientRect();
      const dx=cx-(r.left+r.width/2), dy=cy-(r.top+r.height/2), k=scale/old;
      tx=dx-(dx-tx)*k; ty=dy-(dy-ty)*k; }
    if(scale===MIN){ tx=0; ty=0; }
    clampPan(); apply(); };
  const fit=()=>{ scale=MIN; tx=0; ty=0; apply(); };
  const onKey=e=>{ if(e.key==='Escape'){ e.stopPropagation(); close(); return; }
    if(e.key==='+'||e.key==='=') zoom(1.4);
    else if(e.key==='-'||e.key==='_') zoom(1/1.4);
    else if(e.key==='0') fit(); };
  const cleanup=()=>{ document.removeEventListener('keydown',onKey,true); };
  const close=()=>{ if(closed||!wrap.isConnected) return; closed=true;
    wrap.classList.add('closing');
    setTimeout(()=>{ wrap.remove(); cleanup();
      if(activeModal===api) activeModal=null;
      if(lastFocus&&lastFocus.focus) lastFocus.focus(); },180); };
  const api={ close(instant){ if(closed||!wrap.isConnected) return;
    if(instant){ closed=true; wrap.remove(); cleanup();
      if(activeModal===api) activeModal=null;
      if(lastFocus&&lastFocus.focus) lastFocus.focus(); return; }
    close(); } };
  const ptrs=new Map();
  const dist=()=>{ const a=[...ptrs.values()]; return Math.hypot(a[0].x-a[1].x,a[0].y-a[1].y); };
  const mid=()=>{ const a=[...ptrs.values()]; return {x:(a[0].x+a[1].x)/2,y:(a[0].y+a[1].y)/2}; };
  stage.addEventListener('pointerdown',e=>{
    if(e.pointerType==='mouse'&&e.button!==0) return;
    ptrs.set(e.pointerId,{x:e.clientX,y:e.clientY});
    downOnImg=(e.target===img);
    try{ stage.setPointerCapture(e.pointerId); }catch(err){}
    img.classList.add('dragging');
    if(ptrs.size===1){ dragging=true; moved=false; sx=e.clientX-tx; sy=e.clientY-ty; }
    else if(ptrs.size===2){ dragging=false; pinchD=dist(); }
  });
  stage.addEventListener('pointermove',e=>{
    if(!ptrs.has(e.pointerId)) return;
    const p=ptrs.get(e.pointerId); p.x=e.clientX; p.y=e.clientY;
    if(ptrs.size===1&&dragging&&scale>MIN){
      const nx=e.clientX-sx, ny=e.clientY-sy;
      if(Math.abs(nx-tx)>3||Math.abs(ny-ty)>3) moved=true;
      tx=nx; ty=ny; clampPan(); apply();
    } else if(ptrs.size===2&&pinchD>0){
      const d=dist(), m=mid();
      zoom(d/pinchD,m.x,m.y); pinchD=d; moved=true; }
  });
  const endPtr=e=>{ const touch=e.pointerType==='touch';
    ptrs.delete(e.pointerId);
    if(ptrs.size===1){ const a=[...ptrs.values()][0];
      dragging=true; sx=a.x-tx; sy=a.y-ty; pinchD=0; }
    else if(!ptrs.size){ dragging=false; }
    img.classList.remove('dragging');
    if(touch&&!moved&&!ptrs.size){
      if(!downOnImg){ close(); return; }
      const now=Date.now();
      if(now-lastTapT<320&&Math.abs(e.clientX-lastTapX)<30&&Math.abs(e.clientY-lastTapY)<30){
        zoom(scale>MIN?MIN/scale:2.5/scale,e.clientX,e.clientY);
        lastTapT=0; lastTouchZoom=now;
      } else { lastTapT=now; lastTapX=e.clientX; lastTapY=e.clientY; } }
  };
  stage.addEventListener('pointerup',endPtr);
  stage.addEventListener('pointercancel',endPtr);
  stage.addEventListener('wheel',e=>{ e.preventDefault();
    zoom(e.deltaY<0?1.15:1/1.15,e.clientX,e.clientY); },{passive:false});
  stage.addEventListener('dblclick',e=>{ e.preventDefault();
    if(Date.now()-lastTouchZoom<500) return;
    if(downOnImg) zoom(scale>MIN?MIN/scale:2.5/scale,e.clientX,e.clientY); });
  stage.addEventListener('click',e=>{ if(moved){ moved=false; return; }
    if(!downOnImg) close(); });
  $('#lb-x',wrap).addEventListener('click',()=>api.close());
  $('#lb-in',wrap).addEventListener('click',()=>zoom(1.4));
  $('#lb-out',wrap).addEventListener('click',()=>zoom(1/1.4));
  $('#lb-fit',wrap).addEventListener('click',fit);
  document.addEventListener('keydown',onKey,true);
  activeModal=api; lastFocus=document.activeElement;
  requestAnimationFrame(()=>{ wrap.classList.add('open'); $('#lb-x',wrap).focus(); });
  apply();
  return api;
}

/* ═══ 7. spatial navigation core ═════════════════════════════ */
const stageEl=()=>document.getElementById('stage');
const Nav={
  stack:[], busy:false, invalid:new Set(),
  invalidate(...ids){ ids.forEach(i=>this.invalid.add(i)); },
  parkBehind(){ for(let i=0;i<this.stack.length-1;i++) this.stack[i].el.classList.add('is-parked'); },
  unpark(){ this.stack.forEach(it=>it.el.classList.remove('is-parked')); },
  title(it){ try{ const d=LAYERS[it.id]; return d&&d.title?d.title(it.params):''; }catch(e){ return ''; } },
  init(id){ const def=LAYERS[id]; if(!def) return;
    const item={id,params:{},el:null};
    let el; try{ el=def.render({}); }
    catch(err){ el=document.createElement('section'); el.className='layer';
      el.innerHTML=`<div class="lbody"><div class="wrap">${emptyState('alert','Initialization error','')}</div></div>`; }
    el.classList.add('layer'); el.dataset.layer=id; item.el=el;
    this.stack.push(item); this.updateDepths();
    stageEl().appendChild(el); FX.hubGate(this); },
  replace(id,params){ params=params||{};
    const old=this.stack.pop(); if(old) old.el.remove();
    const def=LAYERS[id]; if(!def) return;
    const item={id,params,el:null};
    let el; try{ el=def.render(params); }
    catch(err){ el=document.createElement('section'); el.className='layer';
      el.innerHTML=`<div class="lbody"><div class="wrap">${emptyState('alert','Something went wrong.','')}</div></div>`; }
    el.classList.add('layer'); el.dataset.layer=id; item.el=el;
    this.stack.push(item); this.updateDepths();
    stageEl().appendChild(el); Hist.sync(); FX.hubGate(this); },
  push(id,params){ if(this.busy) return; params=params||{};
    const def=LAYERS[id]; if(!def) return;
    const item={id,params,el:null}; this.stack.push(item);
    let el; try{ el=def.render(params); }
    catch(err){ el=document.createElement('section'); el.className='layer';
      el.innerHTML=`<div class="lbody"><div class="wrap">${emptyState('alert','Something went wrong.','')}</div></div>`; }
    el.classList.add('layer'); el.dataset.layer=id; item.el=el;
    this.updateDepths();
    el.classList.add('is-enter');
    stageEl().appendChild(el);
    el.getBoundingClientRect();
    this.busy=true;
    requestAnimationFrame(()=>requestAnimationFrame(()=>el.classList.remove('is-enter')));
    Sound.play(this.stack.length>2?'transition':'move');
    Hist.pushDepth(); FX.hubGate(this);
    setTimeout(()=>{ this.busy=false; this.parkBehind(); }, RM.matches?80:600); },
  back(){ if(this.busy||this.stack.length<=1) return;
    if(Hist.ready){ try{ history.back(); return; }catch(e){} }
    this._histBack(1); },
  pop(instant){ if(this.stack.length<=1) return;
    if(instant){ const cur=this.stack.pop(); if(cur) cur.el.remove();
      this.busy=true; this.updateDepths();
      setTimeout(()=>{ this.busy=false; this.parkBehind(); }, RM.matches?80:600);
      Hist.sync(); FX.hubGate(this); return; }
    this.back(); },
  popTo(i){ let g=0;
    while(this.stack.length-1>i&&g++<20){ const it=this.stack.pop(); if(it) it.el.remove(); }
    this.updateDepths(); Hist.sync(); FX.hubGate(this); },
  _histBack(n){ if(this.busy){ Hist.sync(); return; }
    const cur=this.stack[this.stack.length-1]; if(!cur){ Hist.sync(); return; }
    if(LAYERS[cur.id]&&LAYERS[cur.id].onBeforePop&&
       LAYERS[cur.id].onBeforePop(cur)===false){ Hist.pushDepth(); return; }
    for(let i=0;i<n-1;i++){ const it=this.stack.pop(); if(it) it.el.remove(); }
    this.stack.pop(); this.busy=true; this.updateDepths();
    cur.el.classList.add('is-exit'); Sound.play('back');
    setTimeout(()=>{ cur.el.remove(); this.busy=false;
      const top=this.stack[this.stack.length-1];
      if(top&&this.invalid.has(top.id)){ this.invalid.delete(top.id); this.refreshTop(); }
      this.parkBehind(); FX.hubGate(this);
    }, RM.matches?80:600); },
  updateDepths(){ const n=this.stack.length;
    this.stack.forEach((it,i)=>{ const top=(i===n-1);
      it.el.style.setProperty('--depth',String(n-1-i));
      it.el.classList.toggle('is-active',top);
      it.el.classList.toggle('is-behind',!top);
      if(top) it.el.classList.remove('is-parked');
      it.el.setAttribute('aria-hidden',String(!top));
      try{ it.el.inert=!top; }catch(e){} }); },
  refreshTop(){ const top=this.stack[this.stack.length-1]; if(!top) return;
    let fresh; try{ fresh=LAYERS[top.id].render(top.params); }
    catch(err){ return; }
    fresh.classList.add('layer'); fresh.dataset.layer=top.id;
    top.el.replaceWith(fresh); top.el=fresh; this.updateDepths(); FX.hubGate(this); },
  rerenderAll(){ this.stack.slice().forEach(it=>{
      let fresh; try{ fresh=LAYERS[it.id].render(it.params); }catch(err){ return; }
      fresh.classList.add('layer'); fresh.dataset.layer=it.id;
      it.el.replaceWith(fresh); it.el=fresh; });
    this.updateDepths(); this.parkBehind(); FX.hubGate(this); }
};
function chrome(o){ /* standard layer shell: back button + breadcrumb + body */
  const sec=document.createElement('section'); sec.className='layer';
  const crumbs=Nav.stack.map((it,i)=>`<span class="crumb${i===Nav.stack.length-1?' cur':''}">${esc(Nav.title(it))}</span>`)
    .join(`<span class="crumb-sep">${ic('chr','dir-flip')}</span>`);
  sec.innerHTML=`
    <header class="lhead">
      <button class="icon-btn nav-back" aria-label="${t('common.back')}">${ic('chl','dir-flip')}</button>
      <div class="lhead-mid"><span class="lhead-crumbs mono">${crumbs}</span><h1 class="lhead-title">${esc(o.title||'')}</h1></div>
      <div class="lhead-act">${o.actions||''}</div>
    </header>
    <div class="lbody"><div class="wrap">${o.body||''}</div></div>`;
  $('.nav-back',sec).addEventListener('click',()=>Nav.pop());
  return sec;
}
/* global click delegation: data-nav → push; buttons → click sound */
document.addEventListener('click',e=>{
  const nv=e.target.closest('[data-nav]');
  if(nv){ let p={}; try{ p=JSON.parse(nv.dataset.params||'{}'); }catch(err){}
    Nav.push(nv.dataset.nav,p); return; }
  if(e.target.closest('.nav-back')) return; /* back sound handled in pop */
  if(e.target.closest('button,a,.press')) Sound.play('click');
});

/* ═══ 8. pointer engines — card tilt + ambient parallax ══════ */
function bindParallax(){
  const fine=matchMedia('(hover:hover) and (pointer:fine)').matches;
  if(!fine) return; let raf=0,px=0,py=0;
  window.addEventListener('pointermove',e=>{
    px=(e.clientX/innerWidth)*2-1; py=(e.clientY/innerHeight)*2-1;
    if(!raf&&!RM.matches) raf=requestAnimationFrame(()=>{ raf=0;
      document.documentElement.style.setProperty('--px',px.toFixed(3));
      document.documentElement.style.setProperty('--py',py.toFixed(3)); });
  },{passive:true});
}
function bindTilt(){ /* card-level 3D: subtle rotateX/rotateY + moving light */
  if(!matchMedia('(hover:hover) and (pointer:fine)').matches) return;
  let el=null,raf=0;
  document.addEventListener('pointermove',e=>{
    const n=e.target.closest?e.target.closest('.tilt'):null;
    if(n!==el){ if(el){ ['--rx','--ry'].forEach(v=>el.style.setProperty(v,'0deg')); }
      el=n; }
    if(!el||RM.matches) return;
    const r=el.getBoundingClientRect();
    const nx=((e.clientX-r.left)/r.width)*2-1, ny=((e.clientY-r.top)/r.height)*2-1;
    if(!raf) raf=requestAnimationFrame(()=>{ raf=0;
      el.style.setProperty('--rx',(ny*-5).toFixed(2)+'deg');
      el.style.setProperty('--ry',(nx*6).toFixed(2)+'deg');
      el.style.setProperty('--mx',((nx+1)*50).toFixed(1)+'%');
      el.style.setProperty('--my',((ny+1)*50).toFixed(1)+'%'); });
  },{passive:true});
}

/* ═══ 9. domain helpers ══════════════════════════════════════ */
const courseLessons=c=>c.units.flatMap(u=>u.lessons);
function courseProgress(c){ const L=courseLessons(c); const done=L.filter(l=>l.done).length;
  const total=L.length; return {done,total,pct:total?Math.round(done/total*100):0}; }
function unitStats(u){ const done=u.lessons.filter(l=>l.done).length;
  return {done,total:u.lessons.length}; }
function findUnit(p){ const c=state.courses.find(x=>x.id===p.courseId);
  const u=c&&c.units.find(x=>x.id===p.unitId); return [c,u]; }
const courseAccent=c=>['var(--acc-c)','var(--acc-b)','var(--acc-v)'][Math.max(0,state.courses.indexOf(c))%3];
function fmtDue(d){ if(!d) return ''; if(d===today()) return t('common.today');
  const tm=new Date(); tm.setDate(tm.getDate()+1);
  if(d===ymd(tm)) return t('common.tomorrow');
  return fmtDate(parseYmd(d),{day:'numeric',month:'short'}); }
function weekdays(){ let h='';
  for(let i=0;i<7;i++) h+=`<span>${esc(fmtDate(new Date(2023,0,1+i),{weekday:'short'}))}</span>`;
  return h; }

/* ── التقويم الهجري + حساب رمضان (أوفلاين بالكامل) ── */
let HIJRI_FMT=null;
try{ HIJRI_FMT=new Intl.DateTimeFormat('en-u-ca-islamic-umalqura',{day:'numeric',month:'numeric',year:'numeric'}); }
catch(e){ try{ HIJRI_FMT=new Intl.DateTimeFormat('en-u-ca-islamic',{day:'numeric',month:'numeric',year:'numeric'}); }catch(e2){} }
function hijriOf(d){ if(!HIJRI_FMT) return null;
  try{ const o={};
    HIJRI_FMT.formatToParts(d).forEach(p=>{
      if(p.type==='day') o.day=parseInt(p.value,10);
      else if(p.type==='month') o.month=parseInt(p.value,10);
      else if(p.type==='year') o.year=parseInt(p.value,10); });
    return (isFinite(o.day)&&isFinite(o.month))?o:null;
  }catch(e){ return null; } }
function hijriDateStr(){
  const opts={weekday:'long',day:'numeric',month:'long',year:'numeric'};
  try{ return new Intl.DateTimeFormat(loc()+'-u-ca-islamic-umalqura',opts).format(new Date()); }
  catch(e){ try{ return new Intl.DateTimeFormat(loc()+'-u-ca-islamic',opts).format(new Date()); }
    catch(e2){ return ''; } } }
function ramadanInfo(){
  if(!HIJRI_FMT) return null;
  const base=new Date(); base.setHours(12,0,0,0);
  const h=hijriOf(base);
  if(!h) return null;
  if(h.month===9){ /* نحن في رمضان — كم بقي حتى العيد (شوال 1) */
    const d=new Date(base);
    for(let i=1;i<=31;i++){ d.setDate(d.getDate()+1); const hh=hijriOf(d);
      if(hh&&hh.month===10&&hh.day===1) return {phase:'during',days:i-1}; }
    return {phase:'during',days:0}; }
  const d=new Date(base);
  for(let i=1;i<=400;i++){ d.setDate(d.getDate()+1); const hh=hijriOf(d);
    if(hh&&hh.month===9&&hh.day===1) return {phase:'before',days:i}; }
  return null; }
function upcomingFasts(){ /* الاثنين/الخميس + الأيام البيض 13-14-15 هـ */
  const list=[]; const base=new Date(); base.setHours(12,0,0,0);
  for(let i=1;i<=60&&list.length<6;i++){ const d=new Date(base); d.setDate(base.getDate()+i);
    const wd=d.getDay(), h=hijriOf(d);
    if(wd===1) list.push({d,label:t('islam.monday')});
    else if(wd===4) list.push({d,label:t('islam.thursday')});
    if(h&&h.day>=13&&h.day<=15) list.push({d,label:t('islam.whiteDays')+' ('+h.day+')'}); }
  return list; }

/* ─ـ متتبع العبادة اليومي: تصفير تلقائي كل يوم ── */
function islamDay(){ const isl=state.islam, td=today();
  if(isl.day!==td){ isl.day=td;
    isl.prayers={fajr:0,zuhr:0,asr:0,maghrib:0,isha:0};
    isl.rawatib={pf:0,bz:0,az:0,am:0,ai:0};
    isl.morning={}; isl.evening={}; }
  return isl; }
function recordPrayers(){ const isl=state.islam;
  const done=PRAYER_KEYS.filter(k=>isl.prayers[k]>0).length;
  isl.hist[today()]=done;
  const keys=Object.keys(isl.hist).sort();
  while(keys.length>60){ delete isl.hist[keys.shift()]; } }

/* ── خلفيات الثيمات المخصصة (يرفعها المستخدم — محفوظة أوفلاين) ── */
let bgUserEl=null,bgTok=0;
async function bgApply(){
  if(!bgUserEl){ bgUserEl=document.createElement('div'); bgUserEl.className='bg-user';
    bgUserEl.setAttribute('aria-hidden','true'); document.body.prepend(bgUserEl); }
  const tok=++bgTok, th=THEMES.includes(state.settings.theme)?state.settings.theme:'dark';
  let url='';
  try{ const rec=await DB.get('kv','bg-'+th); if(rec&&rec.blob instanceof Blob) url=blobURL(rec.blob); }catch(e){}
  if(tok!==bgTok) return;
  if(url){ bgUserEl.style.backgroundImage=`url("${url}")`; bgUserEl.classList.add('on'); }
  else{ bgUserEl.style.backgroundImage=''; bgUserEl.classList.remove('on'); } }
function uploadBg(th,file){
  if(!/^image\//.test(file.type)){ toast(t('notes.badType'),'err'); return; }
  if(file.size>5*1024*1024){ toast(t('notes.tooBig'),'err'); return; }
  DB.put('kv',{id:'bg-'+th,blob:file}).then(()=>{ bgApply(); toast(t('set.bgApplied')); })
    .catch(()=>toast(t('toast.error'),'err')); }
async function removeBg(th){ try{ await DB.del('kv','bg-'+th); }catch(e){}
  bgApply(); toast(t('set.bgRemoved')); }

/* ═══ 10. charts — hand-drawn canvas, no libraries ═══════════ */
function prepCanvas(c){ const w=c.clientWidth||300, h=parseInt(c.getAttribute('height'),10)||170;
  const dpr=Math.min(2,window.devicePixelRatio||1);
  c.width=w*dpr; c.height=h*dpr; c.style.height=h+'px';
  const x=c.getContext('2d'); x.setTransform(dpr,0,0,dpr,0,0);
  return {x,w,h}; }
function drawLine(c,labels,vals){ try{
  const {x,w,h}=prepCanvas(c); const pl=34,pr=10,pt=12,pb=22,iw=w-pl-pr,ih=h-pt-pb;
  const ink3=cssVar('--ink3'),line=cssVar('--line2'),acc=cssVar('--acc-c');
  x.font='10px '+MONO; x.strokeStyle=line; x.textAlign='right';
  for(let i=0;i<=4;i++){ const y=pt+ih-(i/4)*ih;
    x.globalAlpha=.5; x.beginPath(); x.moveTo(pl,y); x.lineTo(w-pr,y); x.stroke(); x.globalAlpha=1;
    x.fillStyle=ink3; x.fillText(String(i),pl-8,y+3); }
  const px=i=>pl+(vals.length===1?iw/2:(i/(vals.length-1))*iw);
  const py=v=>pt+ih-(v/4)*ih;
  x.beginPath(); x.moveTo(px(0),py(vals[0]));
  vals.forEach((v,i)=>x.lineTo(px(i),py(v)));
  x.lineTo(px(vals.length-1),pt+ih); x.lineTo(px(0),pt+ih); x.closePath();
  x.globalAlpha=.12; x.fillStyle=acc; x.fill(); x.globalAlpha=1;
  x.beginPath(); vals.forEach((v,i)=>i?x.lineTo(px(i),py(v)):x.moveTo(px(i),py(v)));
  x.strokeStyle=acc; x.lineWidth=2.2; x.lineJoin='round'; x.lineCap='round'; x.stroke();
  vals.forEach((v,i)=>{ x.beginPath(); x.arc(px(i),py(v),3,0,7); x.fillStyle=acc; x.fill(); });
  x.fillStyle=ink3; x.textAlign='center';
  const step=Math.ceil(labels.length/8);
  labels.forEach((lb,i)=>{ if(i%step===0) x.fillText(lb,px(i),h-6); });
}catch(e){} }
function drawBars(c,labels,vals,maxV){ try{
  const {x,w,h}=prepCanvas(c); const pl=6,pr=6,pt=10,pb=22,iw=w-pl-pr,ih=h-pt-pb;
  const ink3=cssVar('--ink3'),line=cssVar('--line2'),acc=cssVar('--acc-b');
  const max=Math.max(1,maxV||0,...vals); const n=vals.length, bw=(iw/n)*.55;
  x.strokeStyle=line; x.beginPath(); x.moveTo(pl,pt+ih); x.lineTo(w-pr,pt+ih); x.stroke();
  x.fillStyle=acc;
  vals.forEach((v,i)=>{ const bh=(v/max)*ih, bx=pl+i*(iw/n)+((iw/n)-bw)/2, by=pt+ih-bh;
    if(x.roundRect){ x.beginPath(); x.roundRect(bx,by,bw,Math.max(bh,2),[4,4,0,0]); x.fill(); }
    else x.fillRect(bx,by,bw,Math.max(bh,2)); });
  x.fillStyle=ink3; x.font='10px '+MONO; x.textAlign='center';
  labels.forEach((lb,i)=>x.fillText(lb,pl+i*(iw/n)+(iw/n)/2,h-6));
}catch(e){} }
function drawDonut(c,done,pending){ try{
  const {x,w,h}=prepCanvas(c); const cx=w/2,cy=h/2,r=Math.min(w,h)/2-10;
  const total=done+pending||1, pct=Math.round(done/total*100);
  const line=cssVar('--line2'),acc=cssVar('--acc-c'),ink=cssVar('--ink');
  x.lineWidth=14; x.lineCap='round';
  x.strokeStyle=line; x.beginPath(); x.arc(cx,cy,r,0,Math.PI*2); x.stroke();
  if(done>0){ x.strokeStyle=acc; x.beginPath();
    x.arc(cx,cy,r,-Math.PI/2,-Math.PI/2+(done/total)*Math.PI*2); x.stroke(); }
  x.fillStyle=ink; x.font='700 20px '+MONO; x.textAlign='center'; x.textBaseline='middle';
  x.fillText(pct+'%',cx,cy);
}catch(e){} }

/* ═══ 11. layers — every section of the app ══════════════════ */
const LAYERS={};

/* ── MAIN HUB ─────────────────────────────────────────────── */
LAYERS.hub={
  title:()=>t('nav.hub'),
  render(){
    const sec=document.createElement('section'); sec.className='layer';
    sec.innerHTML='<canvas class="hub-stars" aria-hidden="true"></canvas>';
    const ram=ramadanInfo();
    const cards=[
      {id:'dashboard',icon:'grid',  acc:'acc-c',y:'8px', z:'10px',r:'7deg', fd:'7s',  fdel:'-1s'},
      {id:'courses',  icon:'book',  acc:'acc-v',y:'-10px',z:'46px',r:'4deg', fd:'9s',  fdel:'-3s'},
      {id:'notes',    icon:'note',  acc:'acc-b',y:'-18px',z:'64px',r:'0deg', fd:'8s',  fdel:'-2s'},
      {id:'calendar', icon:'cal',   acc:'acc-c',y:'-10px',z:'46px',r:'-4deg',fd:'10s', fdel:'-5s'},
      {id:'islam',    icon:'mosque',acc:'acc-v',y:'10px', z:'34px',r:'6deg', fd:'7.5s',fdel:'-4s'},
      {id:'analytics',icon:'chart', acc:'acc-b',y:'-6px', z:'58px',r:'2deg', fd:'9.5s',fdel:'-1.5s'},
      {id:'study',    icon:'layers',acc:'acc-c',y:'4px',  z:'40px',r:'-3deg',fd:'8.5s',fdel:'-2.5s'},
      {id:'settings', icon:'sliders',acc:'acc-v',y:'12px',z:'12px',r:'-7deg',fd:'10.5s',fdel:'-6s'},
    ];
    /* += (وليس =) حتى نحافظ على كانفس النجوم المعيّن أعلاه */
    sec.innerHTML+=`
    <div class="lbody hub-body"><div class="wrap">
      <header class="hub-top">
        <div class="brand">${ic('logo','brand-logo')}
          <span class="brand-txt"><span class="brand-name">UBAD</span><span class="brand-sub">ACADEMY HUB</span></span></div>
        <button class="icon-btn" id="hub-search" aria-label="${t('common.search')}">${ic('search')}</button>
      </header>
      <div class="hub-hero">
        <p class="eyebrow">${esc(fmtDateLong(new Date()))}</p>
        <h1 class="hub-title">${t('hub.head')}</h1>
      </div>
      <div class="hub-stage">
        <button class="sat sat-date" data-nav="calendar" data-params='{"date":"${today()}"}'>
          ${ic('cal','ic-s')}<span>${esc(fmtDate(new Date(),{weekday:'short',day:'numeric',month:'short'}))}</span></button>
        <div class="hub-grid" aria-label="${t('app.name')}">
          ${cards.map((c,i)=>`
          <button class="hub-card pre ${c.acc} tilt-target" data-nav="${c.id}"
            style="--i:${i};--y0:${c.y};--z0:${c.z};--ry0:${c.r};--fd:${c.fd};--fdel:${c.fdel}">
            <span class="hub-float"><span class="hub-inner tilt">
              <span class="hub-glow"></span>
              <span class="hub-ic">${ic(c.icon)}</span>
              <span class="hub-tx"><span class="hub-t">${t('nav.'+c.id)}</span><span class="hub-s">${t('sub.'+c.id)}</span></span>
              <span class="hub-arrow">${ic('chr','dir-flip')}</span>
            </span></span>
          </button>`).join('')}
        </div>
        ${ram?`<button class="sat sat-gpa" data-nav="islam">${ic('moon','ic-s')}<span>${t('islam.ramadan')} <b>${ram.days}</b></span></button>`:''}
      </div>
      <p class="hub-foot">${t('hub.foot')}</p>
    </div></div>`;
    $('#hub-search',sec).addEventListener('click',openSearch);
    requestAnimationFrame(()=>requestAnimationFrame(()=>$$('.hub-card',sec).forEach(c=>c.classList.remove('pre'))));
    return sec;
  }
};

/* ── DASHBOARD ────────────────────────────────────────────── */
LAYERS.dashboard={
  title:()=>t('nav.dashboard'),
  render(){
    const td=today();
    const isl=islamDay();
    const pDone=PRAYER_KEYS.filter(k=>isl.prayers[k]>0).length;
    const pendAll=()=>state.tasks.filter(x=>!x.done).length;
    const pend=state.tasks.filter(x=>!x.done)
      .sort((a,b)=>(a.due||'9999')<(b.due||'9999')?-1:1).slice(0,6);
    const upEvs=state.events.filter(e=>e.date>=td)
      .sort((a,b)=>(a.date+(a.time||'')).localeCompare(b.date+(b.time||''))).slice(0,5);
    const recent=state.notes.slice(0,3);
    const taskRow=x=>`<li class="rowitem"><button class="tick task-tick" data-id="${x.id}"
        role="checkbox" aria-checked="false" aria-label="${t('common.done')}">${ic('check','ic-xs')}</button>
      <span class="task-title">${esc(x.title)}</span>
      ${x.due?`<span class="chip mono ${x.due<td?'chip-overdue':''}">${x.due<td?t('dash.overdue'):esc(fmtDue(x.due))}</span>`:''}
      <button class="icon-btn icon-btn-sm task-del" data-id="${x.id}" aria-label="${t('common.delete')}">${ic('trash','ic-s')}</button></li>`;
    const evRow=e=>`<li><button class="rowitem" data-nav="calendar" data-params='{"date":"${e.date}"}'>
      <span class="ev-day"><b>${e.date.slice(8)}</b><span>${esc(fmtDate(parseYmd(e.date),{month:'short'}))}</span></span>
      <span class="row-main"><span class="row-title">${esc(e.title)}</span>
        <span class="row-sub">${e.time?esc(e.time)+' · ':''}${esc(e.desc||'')}</span></span>
      ${ic('chr','ic-s dir-flip row-chev')}</button></li>`;
    const noteRow=n=>`<li><button class="rowitem" data-nav="noteEditor" data-params='${esc(JSON.stringify({id:n.id}))}'>
      <span class="row-ic">${ic('note')}</span>
      <span class="row-main"><span class="row-title">${esc(n.title||t('notes.untitled'))}</span>
        <span class="row-sub">${esc(n.body.slice(0,60))}</span></span>
      <span class="chip mono">${esc(fmtDate(new Date(n.updatedAt),{day:'numeric',month:'short'}))}</span></button></li>`;
    const body=`
    <div style="margin-bottom:18px">
      <p class="eyebrow">${esc(fmtDate(new Date(),{weekday:'long',day:'numeric',month:'long'}))}</p>
      <h1 class="dash-hi">${esc(t('dash.greet.'+greetKey(),{name:state.user.name}))}</h1>
    </div>
    <div class="tiles">
      <button class="tile card" data-nav="islam"><span class="tile-ic">${ic('mosque')}</span><b>${pDone}/5</b><span class="lbl">${t('dash.stPrayers')}</span></button>
      <button class="tile card" data-nav="courses"><span class="tile-ic">${ic('book')}</span><b>${state.courses.length}</b><span class="lbl">${t('dash.stCourses')}</span></button>
      <div class="tile card"><span class="tile-ic">${ic('check')}</span><b>${pendAll()}</b><span class="lbl">${t('dash.stTasks')}</span></div>
      <button class="tile card" data-nav="notes"><span class="tile-ic">${ic('note')}</span><b>${state.notes.length}</b><span class="lbl">${t('dash.stNotes')}</span></button>
    </div>
    <div class="dash-cols">
      <section class="card card-pad">
        <div class="sect-h"><h2>${t('dash.tasks')}</h2></div>
        <div class="quick-add">
          <input class="input" id="q-task" placeholder="${t('dash.addTaskPh')}" maxlength="120" aria-label="${t('dash.taskTitle')}">
          <button class="btn btn-primary" id="q-add">${ic('plus','ic-s')}<span>${t('common.add')}</span></button>
        </div>
        ${pend.length?`<ul class="list">${pend.map(taskRow).join('')}</ul>`:emptyState('check',t('dash.noTasks'))}
      </section>
      <section class="card card-pad">
        <div class="sect-h"><h2>${t('dash.upcoming')}</h2>
          <button class="btn btn-sm" id="q-event">${ic('plus','ic-s')}<span>${t('dash.newEvent')}</span></button></div>
        ${upEvs.length?`<ul class="list">${upEvs.map(evRow).join('')}</ul>`:emptyState('cal',t('dash.noEvents'))}
      </section>
      <section class="card card-pad">
        <div class="sect-h"><h2>${t('dash.recentNotes')}</h2>
          <button class="btn btn-sm" data-nav="noteEditor">${ic('plus','ic-s')}<span>${t('dash.newNote')}</span></button></div>
        ${recent.length?`<ul class="list">${recent.map(noteRow).join('')}</ul>`:emptyState('note',t('dash.noNotes'))}
      </section>
      <section class="card card-pad">
        <div class="sect-h"><h2>${t('dash.quick')}</h2></div>
        <div class="qa-grid">
          <button class="btn" data-nav="noteEditor">${ic('note','ic-s')}<span>${t('dash.newNote')}</span></button>
          <button class="btn" data-nav="islam">${ic('mosque','ic-s')}<span>${t('nav.islam')}</span></button>
          <button class="btn" id="qa-event">${ic('cal','ic-s')}<span>${t('dash.newEvent')}</span></button>
          <button class="btn" data-nav="study">${ic('layers','ic-s')}<span>${t('dash.goStudy')}</span></button>
        </div>
      </section>
    </div>`;
    const sec=chrome({title:t('nav.dashboard'),body});
    const refresh=()=>Nav.refreshTop();
    const addQuick=()=>{ const v=$('#q-task',sec).value.trim(); if(!v) return;
      state.tasks.unshift({id:uid(),title:v.slice(0,120),done:false,due:'',createdAt:Date.now()});
      saveData(); $('#q-task',sec).value=''; refresh(); toast(t('toast.saved')); };
    $('#q-add',sec).addEventListener('click',addQuick);
    $('#q-task',sec).addEventListener('keydown',e=>{ if(e.key==='Enter') addQuick(); });
    $$('.task-tick',sec).forEach(b=>b.addEventListener('click',()=>{
      const x=state.tasks.find(y=>y.id===b.dataset.id);
      if(x){ x.done=true; saveData(); refresh();
        if(!state.tasks.some(y=>!y.done)) FX.confetti(); } }));
    $$('.task-del',sec).forEach(b=>b.addEventListener('click',()=>confirmModal({
      title:t('dash.tasks'),msg:t('common.confirmDelete'),
      onOk:()=>{ state.tasks=state.tasks.filter(y=>y.id!==b.dataset.id); saveData(); refresh(); toast(t('toast.deleted')); } })));
    $('#q-event',sec).addEventListener('click',()=>openEventModal(null,{date:today()},refresh));
    $('#qa-event',sec).addEventListener('click',()=>openEventModal(null,{date:today()},refresh));
    return sec;
  }
};

/* ── COURSES ──────────────────────────────────────────────── */
LAYERS.courses={
  title:()=>t('nav.courses'),
  render(){
    const list=state.courses.length?`<div class="cards-grid">${state.courses.map(c=>{
      const s=courseProgress(c); return `
      <button class="course-card card tilt" data-nav="courseDetail" data-params='{"id":"${c.id}"}'>
        <span class="cc-top mono"><span>${esc(c.code||'—')}</span><span>${c.credits} ${t('courses.cr')}</span></span>
        <span class="cc-name">${esc(c.name)}</span>
        <span class="cc-meta">${esc([c.instructor,c.semester].filter(Boolean).join(' · '))}</span>
        <span class="progress"><i style="width:${s.pct}%;background:${courseAccent(c)}"></i></span>
        <span class="cc-prog mono">${s.done}/${s.total} ${t('courses.lessonsLc')} · ${s.pct}%</span>
      </button>`;}).join('')}</div>`
      : emptyState('book',t('courses.empty'),t('courses.emptyHint'),t('courses.new'));
    const sec=chrome({title:t('nav.courses'),
      actions:`<button class="btn btn-primary btn-sm" id="c-add">${ic('plus','ic-s')}<span>${t('courses.new')}</span></button>`,
      body:list});
    const open=()=>openCourseModal(null,()=>Nav.refreshTop());
    $('#c-add',sec).addEventListener('click',open);
    const cta=$('#es-cta',sec); if(cta) cta.addEventListener('click',open);
    return sec;
  }
};
LAYERS.courseDetail={
  title:p=>{ const c=state.courses.find(x=>x.id===p.id); return c?c.name:t('nav.courses'); },
  render(p){
    const c=state.courses.find(x=>x.id===p.id);
    if(!c) return chrome({title:t('nav.courses'),body:emptyState('book',t('courses.empty'))});
    const s=courseProgress(c);
    const body=`
    <div class="card card-pad">
      <div class="cd-head">
        <div><h2 class="cd-name">${esc(c.name)}</h2>
          <p class="cd-meta">${esc([c.code,c.instructor,c.semester].filter(Boolean).join(' · ')||'—')}</p></div>
        <div class="cd-actions">
          <button class="icon-btn" id="cd-edit" aria-label="${t('common.edit')}">${ic('pen')}</button>
          <button class="icon-btn" id="cd-del" aria-label="${t('common.delete')}">${ic('trash')}</button>
        </div>
      </div>
      <div class="cd-stats mono"><span>${c.credits} ${t('courses.cr')}</span>
        <span>${s.done}/${s.total} ${t('courses.lessonsLc')}</span><span>${s.pct}%</span></div>
      <div class="progress"><i style="width:${s.pct}%;background:${courseAccent(c)}"></i></div>
    </div>
    <div class="sect-h" style="margin-top:22px"><h2>${t('courses.units')}</h2>
      <button class="btn btn-primary btn-sm" id="cd-addunit">${ic('plus','ic-s')}<span>${t('courses.newUnit')}</span></button></div>
    ${c.units.length?`<div class="list">${c.units.map(u=>{ const st=unitStats(u); return `
      <button class="rowitem" data-nav="unit" data-params='${esc(JSON.stringify({courseId:c.id,unitId:u.id}))}'>
        <span class="row-ic">${ic('layers')}</span>
        <span class="row-main"><span class="row-title">${esc(u.title)}</span>
          <span class="row-sub mono">${st.done}/${st.total} ${t('courses.lessonsLc')}</span></span>
        ${ic('chr','ic-s dir-flip row-chev')}</button>`;}).join('')}</div>`
      : emptyState('layers',t('courses.noUnits'))}`;
    const sec=chrome({title:c.name,body});
    $('#cd-edit',sec).addEventListener('click',()=>openCourseModal(c,()=>{
      Nav.invalidate('courses'); Nav.refreshTop(); }));
    $('#cd-del',sec).addEventListener('click',()=>confirmModal({
      title:t('courses.edit'),msg:t('common.confirmDelete'),
      onOk:()=>{ state.courses=state.courses.filter(x=>x.id!==c.id); saveData();
        Nav.invalidate('courses'); Nav.pop(); toast(t('toast.deleted')); } }));
    $('#cd-addunit',sec).addEventListener('click',()=>openModal({
      title:t('courses.newUnit'),
      body:field(t('courses.unitName'),inp('u-title','','')),
      actions:[{label:t('common.cancel')},{label:t('common.add'),cls:'btn-primary',onClick:close=>{
        const v=$('#u-title').value.trim(); if(!v) return;
        c.units.push({id:uid(),title:v.slice(0,80),lessons:[]});
        saveData(); Nav.invalidate('courses'); close(); Nav.refreshTop(); }}]}));
    return sec;
  }
};
LAYERS.unit={
  title:p=>{ const [,u]=findUnit(p); return u?u.title:t('nav.courses'); },
  render(p){
    const [c,u]=findUnit(p);
    if(!u) return chrome({title:t('nav.courses'),body:emptyState('layers',t('courses.noUnits'))});
    const body=`
    <div class="sect-h"><h2>${t('courses.lessons')}</h2>
      <div style="display:flex;gap:8px">
        <button class="icon-btn" id="u-rename" aria-label="${t('common.edit')}">${ic('pen')}</button>
        <button class="btn btn-primary btn-sm" id="u-add">${ic('plus','ic-s')}<span>${t('courses.newLesson')}</span></button>
      </div></div>
    ${u.lessons.length?`<ul class="list">${u.lessons.map(l=>`
      <li class="rowitem lesson-row ${l.done?'done':''}">
        <button class="tick ${l.done?'on':''}" data-lid="${l.id}" role="checkbox"
          aria-checked="${l.done}" aria-label="${t('courses.lessonName')}">${ic('check','ic-xs')}</button>
        <span class="row-main"><span class="row-title l-title">${esc(l.title)}</span></span>
        <button class="icon-btn icon-btn-sm l-del" data-lid="${l.id}" aria-label="${t('common.delete')}">${ic('trash','ic-s')}</button>
      </li>`).join('')}</ul>`
      : emptyState('check',t('courses.noLessons'))}
    <button class="btn btn-danger btn-sm" id="u-del" style="margin-top:18px">${ic('trash','ic-s')}<span>${t('courses.deleteUnit')}</span></button>`;
    const sec=chrome({title:u.title,body});
    $$('.tick',sec).forEach(b=>b.addEventListener('click',()=>{
      const l=u.lessons.find(x=>x.id===b.dataset.lid); if(!l) return;
      l.done=!l.done; saveData(); Nav.invalidate('courseDetail','courses');
      if(u.lessons.length&&u.lessons.every(x=>x.done)) FX.confetti();
      b.classList.toggle('on',l.done); b.setAttribute('aria-checked',String(l.done));
      b.closest('.lesson-row').classList.toggle('done',l.done); }));
    $$('.l-del',sec).forEach(b=>b.addEventListener('click',()=>confirmModal({
      title:t('courses.lessons'),msg:t('common.confirmDelete'),
      onOk:()=>{ u.lessons=u.lessons.filter(x=>x.id!==b.dataset.lid); saveData();
        Nav.invalidate('courseDetail','courses'); Nav.refreshTop(); } })));
    $('#u-add',sec).addEventListener('click',()=>openModal({
      title:t('courses.newLesson'),
      body:field(t('courses.lessonName'),inp('l-title','','')),
      actions:[{label:t('common.cancel')},{label:t('common.add'),cls:'btn-primary',onClick:close=>{
        const v=$('#l-title').value.trim(); if(!v) return;
        u.lessons.push({id:uid(),title:v.slice(0,120),done:false});
        saveData(); Nav.invalidate('courseDetail','courses'); close(); Nav.refreshTop(); }}]}));
    $('#u-rename',sec).addEventListener('click',()=>openModal({
      title:t('common.edit'),
      body:field(t('courses.unitName'),inp('u-title2','',u.title)),
      actions:[{label:t('common.cancel')},{label:t('common.save'),cls:'btn-primary',onClick:close=>{
        const v=$('#u-title2').value.trim(); if(!v) return;
        u.title=v.slice(0,80); saveData(); Nav.invalidate('courseDetail'); close(); Nav.refreshTop(); }}]}));
    $('#u-del',sec).addEventListener('click',()=>confirmModal({
      title:t('courses.deleteUnit'),msg:t('common.confirmDelete'),
      onOk:()=>{ c.units=c.units.filter(x=>x.id!==u.id); saveData();
        Nav.invalidate('courses','courseDetail'); Nav.pop(); toast(t('toast.deleted')); } }));
    return sec;
  }
};
function openCourseModal(c,cb){
  openModal({title:c?t('courses.edit'):t('courses.new'),
    body:`
    ${field(t('courses.name'),inp('cr-name','',c?c.name:''))}
    <div class="f-2col">
    ${field(t('courses.code'),inp('cr-code','',c?c.code:''))}
    ${field(t('courses.credits'),inp('cr-cred','',c?c.credits:3,'number'))}
    </div>
    ${field(t('courses.instructor'),inp('cr-ins','',c?c.instructor:''))}
    ${field(t('courses.semester'),inp('cr-sem','',c?c.semester:''))}`,
    actions:[{label:t('common.cancel')},{label:t('common.save'),cls:'btn-primary',onClick:close=>{
      const name=$('#cr-name').value.trim();
      if(!name){ toast(t('courses.needName'),'err'); return; }
      const data={ name:name.slice(0,80), code:$('#cr-code').value.trim().slice(0,24),
        instructor:$('#cr-ins').value.trim().slice(0,80),
        credits:clampNum($('#cr-cred').value,0,99,3),
        semester:$('#cr-sem').value.trim().slice(0,40) };
      if(c) Object.assign(c,data);
      else state.courses.push(Object.assign({id:uid(),units:[],createdAt:Date.now()},data));
      saveData(); close(); if(cb)cb(); toast(t('toast.saved')); }}]});
}

/* ── NOTES (IndexedDB + image/audio attachments) ──────────── */
LAYERS.notes={
  title:()=>t('nav.notes'),
  render(){
    let q='';
    const sec=chrome({title:t('nav.notes'),
      actions:`<button class="btn btn-primary btn-sm" id="n-new">${ic('plus','ic-s')}<span>${t('notes.new')}</span></button>`,
      body:`
      <div class="search-inline"><span class="si-ic">${ic('search','ic-s')}</span>
        <input class="input" id="n-search" placeholder="${t('notes.searchPh')}" aria-label="${t('common.search')}"></div>
      <div id="n-list" class="notes-grid" style="margin-top:16px"></div>`});
    const listEl=$('#n-list',sec);
    const draw=()=>{ const needle=q.toLowerCase();
      const items=state.notes.filter(n=>!needle||(n.title+' '+n.body+' '+n.tags.join(' ')).toLowerCase().includes(needle));
      listEl.innerHTML=items.length?items.map(n=>`
        <button class="note-card card tilt" data-nav="noteEditor" data-params='${esc(JSON.stringify({id:n.id}))}'>
          <span class="nc-title">${esc(n.title||t('notes.untitled'))}</span>
          <span class="nc-snip">${esc(n.body.slice(0,120))}</span>
          <span class="nc-meta mono">${esc(fmtDate(new Date(n.updatedAt),{day:'numeric',month:'short',year:'numeric'}))}</span>
          <span class="nc-flags">${n.pin?`<span class="chip pin-chip">${ic('pin','ic-xs')}${t('notes.pinned')}</span>`:''}${n.images.length?ic('img','ic-s'):''}${n.audio.length?ic('mic','ic-s'):''}
            ${n.tags.slice(0,2).map(tg=>`<span class="chip">${esc(tg)}</span>`).join('')}</span>
        </button>`).join('')
        :emptyState('note',t('notes.empty'),t('notes.emptyHint'));
    };
    draw();
    $('#n-search',sec).addEventListener('input',e=>{ q=e.target.value; draw(); });
    $('#n-new',sec).addEventListener('click',()=>Nav.push('noteEditor',{}));
    return sec;
  }
};
LAYERS.noteEditor={
  title:()=>t('notes.new'),
  onBeforePop(item){ const api=item.el._ed;
    if(api&&api.isDirty()){ api.confirmDiscard(); return false; } return true; },
  render(p){
    const ex=p.id?state.notes.find(n=>n.id===p.id):null;
    const doc={ id:ex?ex.id:uid(), title:ex?ex.title:'', body:ex?ex.body:'',
      tags:ex?ex.tags.slice():[], createdAt:ex?ex.createdAt:Date.now(),
      pin:ex?!!ex.pin:false,
      images:ex?ex.images.map(a=>({name:a.name,blob:a.blob})):[],
      audio:ex?ex.audio.map(a=>({name:a.name,blob:a.blob})):[], saved:true };
    const sec=chrome({title:ex?t('nav.notes'):t('notes.new'),
      actions:`<button class="icon-btn ${doc.pin?'on':''}" id="ed-pin" aria-label="${t(doc.pin?'notes.unpin':'notes.pin')}">${ic('pin')}</button>
      <button class="btn btn-primary btn-sm" id="ed-save">${ic('check','ic-s')}<span>${t('common.save')}</span></button>`,
      body:`
      <div class="ed">
        <input class="input ed-title" id="ed-title" placeholder="${t('notes.titlePh')}" value="${esc(doc.title)}" maxlength="120" aria-label="${t('notes.titlePh')}">
        <textarea class="input ed-body" id="ed-body" placeholder="${t('notes.bodyPh')}" aria-label="${t('notes.bodyPh')}">${esc(doc.body)}</textarea>
        <input class="input" id="ed-tags" placeholder="${t('notes.tagsPh')}" value="${esc(doc.tags.join(', '))}" aria-label="${t('notes.tagsPh')}">
        <div>
          <div class="sect-h"><h2>${t('notes.images')}</h2>
            <button class="btn btn-sm" id="ed-addimg">${ic('img','ic-s')}<span>${t('notes.attachImage')}</span></button></div>
          <div class="ed-imgs" id="ed-imgs"></div>
          <div class="sect-h" style="margin-top:16px"><h2>${t('notes.audio')}</h2>
            <button class="btn btn-sm" id="ed-addaud">${ic('mic','ic-s')}<span>${t('notes.attachAudio')}</span></button></div>
          <div class="ed-auds" id="ed-auds"></div>
        </div>
        ${ex?`<div><button class="btn btn-danger" id="ed-del">${ic('trash','ic-s')}<span>${t('common.delete')}</span></button></div>`:''}
      </div>
      <input type="file" id="ed-fi" accept="image/jpeg,image/png,image/webp,image/*" multiple hidden>
      <input type="file" id="ed-fa" accept="audio/*" multiple hidden>`});
    const mark=()=>{ doc.saved=false; };
    const drawImgs=()=>{ const box=$('#ed-imgs',sec);
      box.innerHTML=doc.images.map((a,i)=>`
        <figure class="ed-thumb">
          <button class="ed-view" data-i="${i}" aria-label="${t('lb.open')}: ${esc(a.name||'')}" title="${esc(a.name||'')}">
            <img src="${blobURL(a.blob)}" alt="${esc(a.name)}">
          </button>
          <button class="ed-rm" data-i="${i}" aria-label="${t('common.delete')}">${ic('x','ic-xs')}</button>
        </figure>`).join('');
      $$('.ed-view',box).forEach(b=>b.addEventListener('click',()=>{
        const a=doc.images[+b.dataset.i];
        if(a) openLightbox(blobURL(a.blob),a.name); }));
      $$('button.ed-rm',box).forEach(b=>b.addEventListener('click',()=>{
        doc.images.splice(+b.dataset.i,1); drawImgs(); mark(); })); };
    const drawAuds=()=>{ const box=$('#ed-auds',sec);
      box.innerHTML=doc.audio.map((a,i)=>`
        <div class="ed-audio"><span class="ea-name mono">${ic('mic','ic-xs')}${esc(a.name)}</span>
          <audio controls preload="metadata" src="${blobURL(a.blob)}"></audio>
          <button class="icon-btn icon-btn-sm ed-arm" data-i="${i}" aria-label="${t('common.delete')}">${ic('trash','ic-s')}</button></div>`).join('');
      $$('button.ed-arm',box).forEach(b=>b.addEventListener('click',()=>{
        doc.audio.splice(+b.dataset.i,1); drawAuds(); mark(); })); };
    drawImgs(); drawAuds();
    const addFiles=(files,kind)=>{ Array.from(files).forEach(f=>{
      if(f.size>5*1024*1024){ toast(t('notes.tooBig'),'err'); return; }
      if(kind==='image'&&!/^image\//.test(f.type)){ toast(t('notes.badType'),'err'); return; }
      if(kind==='audio'&&!/^audio\//.test(f.type)){ toast(t('notes.badType'),'err'); return; }
      if(kind==='image') doc.images.push({name:f.name||'image',blob:f});
      else doc.audio.push({name:f.name||'audio',blob:f});
      mark(); });
      drawImgs(); drawAuds(); };
    ['ed-title','ed-body','ed-tags'].forEach(id=>$('#'+id,sec).addEventListener('input',mark));
    $('#ed-addimg',sec).addEventListener('click',()=>$('#ed-fi',sec).click());
    $('#ed-addaud',sec).addEventListener('click',()=>$('#ed-fa',sec).click());
    $('#ed-fi',sec).addEventListener('change',e=>{ addFiles(e.target.files,'image'); e.target.value=''; });
    $('#ed-fa',sec).addEventListener('change',e=>{ addFiles(e.target.files,'audio'); e.target.value=''; });
    const save=async()=>{
      doc.title=$('#ed-title',sec).value.trim();
      doc.body=$('#ed-body',sec).value;
      doc.tags=$('#ed-tags',sec).value.split(',').map(s=>s.trim()).filter(Boolean).slice(0,8);
      const rec={ id:doc.id,title:doc.title,body:doc.body,tags:doc.tags,
        createdAt:doc.createdAt,updatedAt:Date.now(),images:doc.images,audio:doc.audio,pin:!!doc.pin };
      await saveNote(rec);
      doc.saved=true; Nav.invalidate('dashboard','notes'); toast(t('toast.saved')); };
    $('#ed-save',sec).addEventListener('click',e=>{ pulse(e.currentTarget); save(); });
    $('#ed-pin',sec).addEventListener('click',()=>{
      doc.pin=!doc.pin;
      const pb=$('#ed-pin',sec); pb.classList.toggle('on',doc.pin);
      pb.setAttribute('aria-label',t(doc.pin?'notes.unpin':'notes.pin'));
      mark(); });
    const delBtn=$('#ed-del',sec);
    if(delBtn) delBtn.addEventListener('click',()=>confirmModal({
      title:t('nav.notes'),msg:t('notes.deleteMsg'),
      onOk:async()=>{ try{ await DB.del('notes',doc.id); }catch(e){}
        state.notes=state.notes.filter(n=>n.id!==doc.id);
        doc.images.concat(doc.audio).forEach(a=>{ try{ URL.revokeObjectURL(blobURL(a.blob)); }catch(e){} });
        doc.saved=true; Nav.invalidate('dashboard','notes'); Nav.pop(); toast(t('toast.deleted')); } }));
    sec._ed={ isDirty:()=>!doc.saved,
      confirmDiscard(){ openModal({ title:t('notes.discard'),
        body:`<p class="m-msg">${t('notes.discardMsg')}</p>`,
        actions:[
          {label:t('common.save'),cls:'btn-primary',onClick:close=>{ close(); doc.saved=true; save(); Nav.pop(); }},
          {label:t('notes.discardBtn'),cls:'btn-danger',onClick:close=>{ close(); doc.saved=true; Nav.pop(); }},
          {label:t('common.cancel')}]}); } };
    return sec;
  }
};

/* ── CALENDAR ─────────────────────────────────────────────── */
LAYERS.calendar={
  title:()=>t('nav.calendar'),
  render(p){
    const td=today();
    if(!p.view){ const n=new Date(); p.view={y:n.getFullYear(),m:n.getMonth()}; }
    let sel=p.date||td;
    const sec=chrome({title:t('nav.calendar'),
      actions:`<button class="btn btn-primary btn-sm" id="cal-add">${ic('plus','ic-s')}<span>${t('cal.new')}</span></button>`,
      body:`
      <div class="cal">
        <div class="cal-top">
          <button class="icon-btn" id="cal-prev" aria-label="${t('cal.prev')}">${ic('chl','dir-flip')}</button>
          <div class="cal-month mono" id="cal-month"></div>
          <button class="icon-btn" id="cal-next" aria-label="${t('cal.next')}">${ic('chr','dir-flip')}</button>
          <button class="btn btn-sm" id="cal-today">${t('common.today')}</button>
        </div>
        <div class="cal-dow">${weekdays()}</div>
        <div class="cal-grid" id="cal-grid"></div>
        <div class="cal-side card card-pad">
          <h2 class="cal-dayhead" id="cal-dayhead"></h2>
          <div id="cal-list"></div>
        </div>
      </div>`});
    const grid=$('#cal-grid',sec);
    const draw=()=>{
      const {y,m}=p.view;
      $('#cal-month',sec).textContent=fmtDate(new Date(y,m,1),{month:'long',year:'numeric'});
      const first=new Date(y,m,1).getDay(), days=new Date(y,m+1,0).getDate();
      let html=''; for(let i=0;i<first;i++) html+='<span class="cal-cell pad"></span>';
      for(let d=1;d<=days;d++){ const ds=ymd(new Date(y,m,d));
        const n=state.events.filter(e=>e.date===ds).length;
        html+=`<button class="cal-cell ${ds===td?'today':''} ${ds===sel?'sel':''}" data-d="${ds}"
          aria-label="${ds}">${d}${n?`<span class="cal-dots">${'<i></i>'.repeat(Math.min(3,n))}</span>`:''}</button>`; }
      grid.innerHTML=html;
      $$('button.cal-cell',grid).forEach(b=>b.addEventListener('click',()=>{ sel=b.dataset.d; draw(); }));
      $('#cal-dayhead',sec).textContent=fmtDate(parseYmd(sel),{weekday:'long',day:'numeric',month:'long'});
      const evs=state.events.filter(e=>e.date===sel).sort((a,b)=>(a.time||'').localeCompare(b.time||''));
      $('#cal-list',sec).innerHTML=evs.length?`<ul class="list">${evs.map(e=>`
        <li class="rowitem"><span class="row-main"><span class="row-title">${esc(e.title)}</span>
          ${e.time?`<span class="chip mono">${esc(e.time)}</span>`:''}
          ${e.desc?`<span class="row-sub">${esc(e.desc)}</span>`:''}</span>
          <button class="icon-btn icon-btn-sm ev-edit" data-id="${e.id}" aria-label="${t('common.edit')}">${ic('pen','ic-s')}</button>
          <button class="icon-btn icon-btn-sm ev-del" data-id="${e.id}" aria-label="${t('common.delete')}">${ic('trash','ic-s')}</button>
        </li>`).join('')}</ul>`
        :`<div class="empty empty-sm">${ic('cal')}<p>${t('cal.none')}</p></div>`;
      $$('.ev-edit',sec).forEach(b=>b.addEventListener('click',()=>
        openEventModal(state.events.find(x=>x.id===b.dataset.id),{},draw)));
      $$('.ev-del',sec).forEach(b=>b.addEventListener('click',()=>confirmModal({
        title:t('cal.edit'),msg:t('cal.deleteMsg'),
        onOk:()=>{ state.events=state.events.filter(x=>x.id!==b.dataset.id); saveData(); draw(); toast(t('toast.deleted')); } })));
    };
    const shift=dm=>{ p.view.m+=dm;
      if(p.view.m<0){p.view.m=11;p.view.y--;} if(p.view.m>11){p.view.m=0;p.view.y++;}
      draw(); };
    $('#cal-prev',sec).addEventListener('click',()=>shift(-1));
    $('#cal-next',sec).addEventListener('click',()=>shift(1));
    $('#cal-today',sec).addEventListener('click',()=>{ const n=new Date();
      p.view={y:n.getFullYear(),m:n.getMonth()}; sel=td; draw(); });
    $('#cal-add',sec).addEventListener('click',()=>openEventModal(null,{date:sel},draw));
    /* contextual swipe: changes month ONLY — never navigates layers */
    let sx=0,sy=0;
    grid.addEventListener('touchstart',e=>{ sx=e.touches[0].clientX; sy=e.touches[0].clientY; },{passive:true});
    grid.addEventListener('touchend',e=>{
      const dx=e.changedTouches[0].clientX-sx, dy=e.changedTouches[0].clientY-sy;
      if(Math.abs(dx)>60&&Math.abs(dx)>Math.abs(dy)*2){
        const rtl=document.documentElement.dir==='rtl';
        const next=rtl?dx<-60:dx>60; shift(next?1:-1); } },{passive:true});
    draw();
    return sec;
  }
};
function openEventModal(ev,dflt,cb){
  openModal({title:ev?t('cal.edit'):t('cal.new'),
    body:`
    ${field(t('cal.eventTitle'),inp('ev-title','',ev?ev.title:''))}
    <div class="f-2col">
      ${field(t('cal.date'),`<input class="input" id="ev-date" type="date" value="${ev?ev.date:(dflt&&dflt.date)||today()}">`)}
      ${field(t('cal.time')+' ('+t('common.optional')+')',`<input class="input" id="ev-time" type="time" value="${ev?ev.time||'':''}">`)}
    </div>
    ${field(t('cal.desc'),`<textarea class="input" id="ev-desc" rows="3">${esc(ev?ev.desc:'')}</textarea>`)}`,
    actions:[{label:t('common.cancel')},{label:t('common.save'),cls:'btn-primary',onClick:close=>{
      const title=$('#ev-title').value.trim(), date=$('#ev-date').value;
      if(!title||!date){ toast(t('cal.needTitle'),'err'); return; }
      if(ev){ ev.title=title.slice(0,120); ev.date=date; ev.time=$('#ev-time').value; ev.desc=$('#ev-desc').value.trim().slice(0,500); }
      else state.events.push({id:uid(),title:title.slice(0,120),date,time:$('#ev-time').value,
        desc:$('#ev-desc').value.trim().slice(0,500),createdAt:Date.now()});
      saveData(); close(); if(cb)cb(); toast(t('toast.saved')); }}]});
}
function openTaskModal(cb){
  openModal({title:t('dash.newTask'),
    body:`${field(t('dash.taskTitle'),inp('tk-title','',''))}
      ${field(t('dash.taskDue'),`<input class="input" id="tk-due" type="date" value="${today()}">`)}`,
    actions:[{label:t('common.cancel')},{label:t('common.add'),cls:'btn-primary',onClick:close=>{
      const v=$('#tk-title').value.trim(); if(!v) return;
      state.tasks.unshift({id:uid(),title:v.slice(0,120),done:false,due:$('#tk-due').value||'',createdAt:Date.now()});
      saveData(); close(); if(cb)cb(); toast(t('toast.saved')); }}]});
}

/* ── ANA MUSLIM — القسم الإسلامي الكامل ─────────────────── */
LAYERS.islam={
  title:()=>t('nav.islam'),
  render(){
    const isl=islamDay();
    const ram=ramadanInfo();
    const hij=hijriDateStr();
    const pDone=PRAYER_KEYS.filter(k=>isl.prayers[k]>0).length;
    const rDone=RAWATIB_KEYS.filter(k=>isl.rawatib[k]>0).length;
    const rakah=RAWATIB_KEYS.reduce((a,k)=>a+(isl.rawatib[k]?RAWATIB_RAK[k]:0),0);
    const mDone=MORNING_ADHKAR.filter(k=>isl.morning[k]).length;
    const eDone=EVENING_ADHKAR.filter(k=>isl.evening[k]).length;
    const td=today();
    const hNow=hijriOf(new Date());
    const isMonThu=(()=>{ const w=new Date().getDay(); return w===1||w===4; })();
    const isWhite=!!(hNow&&hNow.day>=13&&hNow.day<=15);
    const fastingToday=isl.fasts.includes(td);
    const upcoming=upcomingFasts();
    const prayerRow=k=>{ const v=isl.prayers[k];
      return `<li class="rowitem p-row">
        <button class="tick p-tick ${v>0?'on':''}" data-p="${k}" role="checkbox" aria-checked="${v>0}" aria-label="${t('islam.p.'+k)}">${ic('check','ic-xs')}</button>
        <span class="row-main"><span class="row-title">${t('islam.p.'+k)}</span></span>
        <button class="chip p-jam ${v===2?'chip-ok':''}" data-p="${k}" role="checkbox" aria-checked="${v===2}" aria-label="${t('islam.jamaah')}">${ic('mosque','ic-xs')}<span>${t('islam.jamaah')}</span></button>
      </li>`; };
    const rawRow=k=>`<li class="rowitem">
        <button class="tick r-tick ${isl.rawatib[k]?'on':''}" data-r="${k}" role="checkbox" aria-checked="${isl.rawatib[k]}" aria-label="${t('islam.r.'+k)}">${ic('check','ic-xs')}</button>
        <span class="row-main"><span class="row-title">${t('islam.r.'+k)}</span></span>
      </li>`;
    const adhRow=(k,which)=>`<li class="rowitem adh-row">
        <button class="tick a-tick ${isl[which][k]?'on':''}" data-w="${which}" data-a="${k}" role="checkbox" aria-checked="${isl[which][k]}" aria-label="${t('islam.'+k)}">${ic('check','ic-xs')}</button>
        <span class="row-main"><span class="row-title adh-name">${t('islam.'+k)}</span></span>
      </li>`;
    const body=`
    <div class="card card-pad ram-hero">
      <div class="ram-info">
        <p class="ram-peace">${t('islam.peace')}</p>
        ${hij?`<p class="eyebrow" style="margin-top:10px">${esc(hij)}</p>`:''}
        <p class="ram-greg mono">${esc(fmtDateLong(new Date()))}</p>
      </div>
      ${ram?`<div class="ram-count">
        <p class="ram-cap">${t(ram.phase==='during'?'islam.ramMubarak':'islam.ramIn')}</p>
        <b class="ram-num mono">${ram.days}</b>
        <span class="ram-days">${t(ram.phase==='during'?'islam.ramLeft':'islam.ramDays')}</span>
      </div>`:''}
    </div>
    <div class="card card-pad islam-card">
      <div class="sect-h"><h2>${t('islam.prayers')}</h2><span class="chip mono" id="pr-stat">${pDone} / 5</span></div>
      <ul class="list">${PRAYER_KEYS.map(prayerRow).join('')}</ul>
      <div class="progress" style="margin-top:14px"><i id="pr-bar" style="width:${pDone/5*100}%;background:var(--acc-c)"></i></div>
    </div>
    <div class="card card-pad islam-card">
      <div class="sect-h"><h2>${t('islam.rawatib')}</h2><span class="chip mono" id="rw-stat">${rDone} / 5 · ${rakah} / 12 ${t('islam.rak')}</span></div>
      <ul class="list">${RAWATIB_KEYS.map(rawRow).join('')}</ul>
      <div class="progress" style="margin-top:14px"><i id="rw-bar" style="width:${rDone/5*100}%;background:var(--acc-v)"></i></div>
    </div>
    <div class="sect-h islam-head"><h2>${t('islam.adhkar')}</h2></div>
    <div class="adh-grid">
      <div class="card card-pad adh-card">
        <div class="sect-h"><h2>${ic('sun','ic-s')} ${t('islam.morning')}</h2><span class="chip mono" id="ad-m-stat">${mDone} / 6</span></div>
        <ul class="list">${MORNING_ADHKAR.map(k=>adhRow(k,'morning')).join('')}</ul>
      </div>
      <div class="card card-pad adh-card">
        <div class="sect-h"><h2>${ic('moon','ic-s')} ${t('islam.evening')}</h2><span class="chip mono" id="ad-e-stat">${eDone} / 6</span></div>
        <ul class="list">${EVENING_ADHKAR.map(k=>adhRow(k,'evening')).join('')}</ul>
      </div>
    </div>
    <div class="card card-pad islam-card">
      <div class="sect-h"><h2>${t('islam.tasbih')}</h2>
        <button class="btn btn-sm" id="tas-reset">${ic('refresh','ic-s')}<span>${t('islam.tasbihReset')}</span></button></div>
      <div class="pillrow tas-modes">
        ${TASBIH_MODES.map(m=>`<button class="pill tas-mode ${isl.tasbih.mode===m?'on':''}" data-m="${m}">${t('islam.t.'+m)}</button>`).join('')}
      </div>
      <button class="tas-tap" id="tas-tap" aria-label="${t('islam.t.'+isl.tasbih.mode)}">
        <b class="mono" id="tas-count">${isl.tasbih.count}</b>
        <span class="mono" id="tas-goal">/ ${isl.tasbih.target}</span>
      </button>
      <div class="pillrow" style="justify-content:center">
        ${TASBIH_TARGETS.map(g=>`<button class="pill tas-goal ${isl.tasbih.target===g?'on':''}" data-g="${g}">${g}</button>`).join('')}
      </div>
      <p class="tas-total mono">${t('islam.tasbihTotal')}: <b id="tas-total">${isl.tasbih.total}</b></p>
    </div>
    <div class="card card-pad islam-card">
      <div class="sect-h"><h2>${t('islam.quran')}</h2><span class="chip mono" id="qz-stat">${isl.quran.juz} / 30</span></div>
      <div class="progress"><i id="qz-bar" style="width:${isl.quran.juz/30*100}%;background:var(--acc-b)"></i></div>
      <div class="qz-ctl">
        <button class="icon-btn" id="qz-sub" aria-label="−1">−</button>
        <button class="icon-btn" id="qz-add" aria-label="+1">+</button>
        <div class="pillrow">
          ${[1,2,3].map(g=>`<button class="pill qz-goal ${isl.quran.target===g?'on':''}" data-g="${g}">${g} / ${t('common.today')}</button>`).join('')}
        </div>
      </div>
      <p class="f-hint" id="qz-eta"></p>
      <button class="btn btn-primary btn-sm" id="qz-new" style="display:none;margin-top:8px">${ic('refresh','ic-s')}<span>${t('islam.qNew')}</span></button>
    </div>
    <div class="card card-pad islam-card">
      <div class="sect-h"><h2>${t('islam.fasting')}</h2><span class="chip mono">${isl.fasts.length} ${t('islam.totalFasts')}</span></div>
      <div class="rowitem fast-row">
        <span class="row-main"><span class="row-title">${t('islam.fastToday')}</span>
          ${(isMonThu||isWhite)?`<span class="row-sub">${t(isWhite?'islam.whiteDays':'islam.sunnahDay')}</span>`:''}</span>
        <button class="switch ${fastingToday?'on':''}" id="fs-today" role="switch" aria-checked="${fastingToday}" aria-label="${t('islam.fastToday')}"></button>
      </div>
      <div class="sect-h islam-head"><h2>${t('islam.upcoming')}</h2></div>
      ${upcoming.length?`<div class="list">${upcoming.map(u=>`
        <div class="rowitem fs-up-row">
          <span class="chip mono">${esc(fmtDate(u.d,{weekday:'short',day:'numeric',month:'short'}))}</span>
          <span class="row-main"><span class="row-title">${esc(u.label)}</span></span>
        </div>`).join('')}</div>`:`<div class="empty empty-sm">${ic('cal')}<p>${t('ana.noData')}</p></div>`}
    </div>`;
    const sec=chrome({title:t('nav.islam'),body});
    /* ── الصلوات: تحديث في المكان (بدون فقدان مكان السكرول) ── */
    const syncPrayers=()=>{ const done=PRAYER_KEYS.filter(k=>state.islam.prayers[k]>0).length;
      const st=$('#pr-stat',sec); if(st) st.textContent=done+' / 5';
      const bar=$('#pr-bar',sec); if(bar) bar.style.width=(done/5*100)+'%';
      PRAYER_KEYS.forEach(k=>{ const v=state.islam.prayers[k];
        const tk=$(`.p-tick[data-p="${k}"]`,sec);
        if(tk){ tk.classList.toggle('on',v>0); tk.setAttribute('aria-checked',String(v>0)); }
        const jm=$(`.p-jam[data-p="${k}"]`,sec);
        if(jm){ jm.classList.toggle('chip-ok',v===2); jm.setAttribute('aria-checked',String(v===2)); } }); };
    $$('.p-tick',sec).forEach(b=>b.addEventListener('click',()=>{
      const k=b.dataset.p, before=PRAYER_KEYS.filter(x=>state.islam.prayers[x]>0).length;
      state.islam.prayers[k]=state.islam.prayers[k]===1?0:1;
      recordPrayers(); saveData(); syncPrayers();
      const after=PRAYER_KEYS.filter(x=>state.islam.prayers[x]>0).length;
      if(after===5&&before<5){ FX.confetti(); toast(t('islam.prayersDone5')); } }));
    $$('.p-jam',sec).forEach(b=>b.addEventListener('click',()=>{
      const k=b.dataset.p;
      state.islam.prayers[k]=state.islam.prayers[k]===2?1:2;
      recordPrayers(); saveData(); syncPrayers();
      if(PRAYER_KEYS.every(x=>state.islam.prayers[x]>0)) FX.confetti(); }));
    /* ── الرواتب ── */
    const syncRaw=()=>{ const done=RAWATIB_KEYS.filter(k=>state.islam.rawatib[k]>0).length;
      const rk=RAWATIB_KEYS.reduce((a,k)=>a+(state.islam.rawatib[k]?RAWATIB_RAK[k]:0),0);
      const st=$('#rw-stat',sec); if(st) st.textContent=done+' / 5 · '+rk+' / 12 '+t('islam.rak');
      const bar=$('#rw-bar',sec); if(bar) bar.style.width=(done/5*100)+'%';
      $$('.r-tick',sec).forEach(b=>{ const on=state.islam.rawatib[b.dataset.r];
        b.classList.toggle('on',on); b.setAttribute('aria-checked',String(on)); }); };
    $$('.r-tick',sec).forEach(b=>b.addEventListener('click',()=>{
      const k=b.dataset.r; state.islam.rawatib[k]=state.islam.rawatib[k]?0:1;
      saveData(); syncRaw();
      if(RAWATIB_KEYS.every(x=>state.islam.rawatib[x])) FX.confetti(); }));
    /* ── الأذكار ── */
    const syncAdh=()=>{ const m=MORNING_ADHKAR.filter(k=>state.islam.morning[k]).length;
      const e=EVENING_ADHKAR.filter(k=>state.islam.evening[k]).length;
      const ms=$('#ad-m-stat',sec); if(ms) ms.textContent=m+' / 6';
      const es=$('#ad-e-stat',sec); if(es) es.textContent=e+' / 6';
      $$('.a-tick',sec).forEach(b=>{ const on=state.islam[b.dataset.w][b.dataset.a];
        b.classList.toggle('on',on); b.setAttribute('aria-checked',String(on)); }); };
    $$('.a-tick',sec).forEach(b=>b.addEventListener('click',()=>{
      const w=b.dataset.w, k=b.dataset.a;
      state.islam[w][k]=!state.islam[w][k];
      saveData(); syncAdh();
      if(MORNING_ADHKAR.every(x=>state.islam.morning[x])&&w==='morning') FX.confetti();
      if(EVENING_ADHKAR.every(x=>state.islam.evening[x])&&w==='evening') FX.confetti(); }));
    /* ── المسبحة ── */
    const tasCount=$('#tas-count',sec), tasGoal=$('#tas-goal',sec), tasTotal=$('#tas-total',sec);
    const tasSync=()=>{ const T=state.islam.tasbih;
      tasCount.textContent=T.count; tasGoal.textContent='/ '+T.target; tasTotal.textContent=T.total;
      $$('.tas-mode',sec).forEach(x=>x.classList.toggle('on',x.dataset.m===T.mode));
      $$('.tas-goal',sec).forEach(x=>x.classList.toggle('on',+x.dataset.g===T.target)); };
    $('#tas-tap',sec).addEventListener('click',()=>{
      const T=state.islam.tasbih;
      T.count++; T.total++; buzz(12);
      if(T.count>=T.target){ toast(t('islam.tasbihDone',{n:T.target})); FX.confetti(); T.count=0; }
      saveData(); tasSync(); });
    $$('.tas-mode',sec).forEach(b=>b.addEventListener('click',()=>{
      state.islam.tasbih.mode=b.dataset.m; state.islam.tasbih.count=0;
      saveData(); tasSync(); }));
    $$('.tas-goal',sec).forEach(b=>b.addEventListener('click',()=>{
      state.islam.tasbih.target=+b.dataset.g; state.islam.tasbih.count=0;
      saveData(); tasSync(); }));
    $('#tas-reset',sec).addEventListener('click',()=>{
      state.islam.tasbih.count=0; saveData(); tasSync(); });
    /* ── الختمة ── */
    const qzSync=()=>{ const q=state.islam.quran;
      const st=$('#qz-stat',sec); if(st) st.textContent=q.juz+' / 30';
      const bar=$('#qz-bar',sec); if(bar) bar.style.width=(q.juz/30*100)+'%';
      const eta=$('#qz-eta',sec), nw=$('#qz-new',sec);
      const left=30-q.juz;
      if(left<=0){ eta.textContent=t('islam.qDone'); if(nw) nw.style.display=''; }
      else{ const days=Math.ceil(left/q.target); const d=new Date(); d.setDate(d.getDate()+days);
        eta.textContent=t('islam.qEta')+': '+fmtDate(d,{day:'numeric',month:'short',year:'numeric'})+' ('+days+' '+t('islam.ramDays')+')';
        if(nw) nw.style.display='none'; }
      $$('.qz-goal',sec).forEach(x=>x.classList.toggle('on',+x.dataset.g===q.target)); };
    $('#qz-add',sec).addEventListener('click',()=>{
      const q=state.islam.quran;
      if(q.juz>=30) return;
      q.juz++; if(q.juz===30){ FX.confetti(); toast(t('islam.qDone')); }
      saveData(); qzSync(); });
    $('#qz-sub',sec).addEventListener('click',()=>{
      const q=state.islam.quran; if(q.juz<=0) return;
      q.juz--; saveData(); qzSync(); });
    $$('.qz-goal',sec).forEach(b=>b.addEventListener('click',()=>{
      state.islam.quran.target=+b.dataset.g; saveData(); qzSync(); }));
    $('#qz-new',sec).addEventListener('click',()=>{
      state.islam.quran.juz=0; state.islam.quran.started=Date.now();
      saveData(); qzSync(); });
    /* ── الصيام ── */
    $('#fs-today',sec).addEventListener('click',()=>{
      const isl2=state.islam, td2=today();
      const i=isl2.fasts.indexOf(td2);
      if(i>-1) isl2.fasts.splice(i,1); else isl2.fasts.push(td2);
      saveData();
      const sw=$('#fs-today',sec), on=isl2.fasts.includes(td2);
      sw.classList.toggle('on',on); sw.setAttribute('aria-checked',String(on)); });
    qzSync();
    return sec;
  }
};

/* ── ANALYTICS ────────────────────────────────────────────── */
LAYERS.analytics={
  title:()=>t('nav.analytics'),
  render(){
    const isl=islamDay();
    const pDone=PRAYER_KEYS.filter(k=>isl.prayers[k]>0).length;
    const lessons=state.courses.flatMap(c=>courseLessons(c));
    const lDone=lessons.filter(l=>l.done).length;
    const doneT=state.tasks.filter(x=>x.done).length, pendT=state.tasks.length-doneT;
    const pLabels=[], pDays=[];
    for(let i=6;i>=0;i--){ const d=new Date(); d.setDate(d.getDate()-i);
      pLabels.push(fmtDate(d,{weekday:'short'}));
      pDays.push(state.islam.hist[ymd(d)]||0); }
    const now=new Date(), months=[], counts=[];
    for(let i=5;i>=0;i--){ const d1=new Date(now.getFullYear(),now.getMonth()-i,1);
      const d2=new Date(now.getFullYear(),now.getMonth()-i+1,1);
      months.push(fmtDate(d1,{month:'short'}));
      counts.push(state.notes.filter(n=>n.createdAt>=d1.getTime()&&n.createdAt<d2.getTime()).length); }
    const tiles=[['note',state.notes.length,t('ana.stat.notes')],
      ['layers',state.decks.reduce((a,d)=>a+d.cards.length,0),t('ana.stat.cards')],
      ['mosque',`${pDone}/5`,t('ana.stat.prayers')],
      ['book',`${isl.quran.juz}/30`,t('ana.stat.quran')]];
    const noData=()=>`<div class="empty empty-sm">${ic('chart')}<p>${t('ana.noData')}</p></div>`;
    const body=`
    <div class="tiles">${tiles.map(x=>`
      <div class="tile card"><span class="tile-ic">${ic(x[0])}</span>
        <b class="mono">${esc(String(x[1]))}</b><span class="lbl">${x[2]}</span></div>`).join('')}</div>
    <div class="ana-grid">
      <div class="card card-pad"><div class="sect-h"><h2>${t('ana.prayersTrend')}</h2></div>
        <canvas class="chart" id="ch-prayers" height="170"></canvas></div>
      <div class="card card-pad"><div class="sect-h"><h2>${t('ana.tasksDonut')}</h2></div>
        ${state.tasks.length?`<div class="donut-wrap"><canvas id="ch-tasks" width="150" height="150" role="img"
          aria-label="${t('ana.tasksDonut')}"></canvas>
          <div class="donut-leg"><span><i style="background:var(--acc-c)"></i>${t('ana.done')} · ${doneT}</span>
          <span><i style="background:var(--line2)"></i>${t('ana.pending')} · ${pendT}</span></div></div>`:noData()}</div>
      <div class="card card-pad"><div class="sect-h"><h2>${t('ana.notesActivity')}</h2></div>
        ${state.notes.length?`<canvas class="chart" id="ch-notes" height="170"></canvas>`:noData()}</div>
      <div class="card card-pad"><div class="sect-h"><h2>${t('ana.courseProgress')}</h2></div>
        ${state.courses.length?state.courses.map(c=>{ const s=courseProgress(c); return `
          <div class="pb-row"><span class="pb-name">${esc(c.name)}</span>
            <span class="progress"><i style="width:${s.pct}%;background:${courseAccent(c)}"></i></span>
            <span class="pb-val mono">${s.pct}%</span></div>`;}).join(''):noData()}</div>
    </div>`;
    const sec=chrome({title:t('nav.analytics'),body});
    /* draw once the layer is in the DOM (canvas needs layout) */
    requestAnimationFrame(()=>{
      drawBars($('#ch-prayers',sec),pLabels,pDays,5);
      if(state.notes.length) drawBars($('#ch-notes',sec),months,counts);
      if(state.tasks.length) drawDonut($('#ch-tasks',sec),doneT,pendT); });
    return sec;
  }
};

/* ── STUDY TOOLS ──────────────────────────────────────────── */
let studyTab='cards'; /* يحفظ التاب النشط عبر إعادة الرسم */
LAYERS.study={
  title:()=>t('nav.study'),
  render(){
    let tab=studyTab;
    const sec=chrome({title:t('nav.study'),
      actions:`<button class="btn btn-primary btn-sm" id="st-add">${ic('plus','ic-s')}<span id="st-add-l">${t('study.newDeck')}</span></button>`,
      body:`
      <div class="seg" role="tablist">
        <button class="${tab==='cards'?'on':''}" data-tab="cards" role="tab" aria-selected="${tab==='cards'}">${t('study.tabCards')}</button>
        <button class="${tab==='quiz'?'on':''}" data-tab="quiz" role="tab" aria-selected="${tab==='quiz'}">${t('study.tabQuiz')}</button>
        <button class="${tab==='focus'?'on':''}" data-tab="focus" role="tab" aria-selected="${tab==='focus'}">${ic('timer','ic-s')} ${t('focus.tab')}</button>
      </div>
      <div id="st-pane" style="margin-top:16px"></div>`});
    const pane=$('#st-pane',sec), addBtn=$('#st-add',sec);
    const drawDecks=()=>{ $('#st-add-l',sec).textContent=t('study.newDeck');
      pane.innerHTML=state.decks.length?`<div class="list">${state.decks.map(d=>`
        <div class="rowitem" data-deck="${d.id}" role="button" tabindex="0">
          <span class="row-ic">${ic('layers')}</span>
          <span class="row-main"><span class="row-title">${esc(d.title)}</span>
            <span class="row-sub mono">${d.cards.length} ${t('study.cardsLc')}</span></span>
          <button class="btn btn-sm dk-study" data-id="${d.id}">${ic('arrow','ic-s dir-flip')}<span>${t('study.study')}</span></button>
          <button class="icon-btn icon-btn-sm dk-del" data-id="${d.id}" aria-label="${t('common.delete')}">${ic('trash','ic-s')}</button>
        </div>`).join('')}</div>`
        :emptyState('layers',t('study.noDecks'),t('study.noDecksHint'));
      $$('.dk-study',pane).forEach(b=>b.addEventListener('click',e=>{
        e.stopPropagation(); Nav.push('deck',{deckId:b.dataset.id}); }));
      $$('.dk-del',pane).forEach(b=>b.addEventListener('click',e=>{ e.stopPropagation();
        confirmModal({title:t('study.deleteDeck'),msg:t('study.deleteDeckMsg'),
          onOk:()=>{ state.decks=state.decks.filter(d=>d.id!==b.dataset.id); saveData(); drawDecks(); toast(t('toast.deleted')); } }); }));
      $$('[data-deck]',pane).forEach(r=>{ r.addEventListener('click',()=>Nav.push('deck',{deckId:r.dataset.deck}));
        r.addEventListener('keydown',e=>{ if(e.key==='Enter') Nav.push('deck',{deckId:r.dataset.deck}); }); }); };
    const drawQuizzes=()=>{ $('#st-add-l',sec).textContent=t('study.newQuiz');
      pane.innerHTML=state.quizzes.length?`<div class="list">${state.quizzes.map(z=>`
        <div class="rowitem" data-quiz="${z.id}" role="button" tabindex="0">
          <span class="row-ic">${ic('check')}</span>
          <span class="row-main"><span class="row-title">${esc(z.title)}</span>
            <span class="row-sub mono">${z.questions.length} ${t('study.questionsLc')}</span></span>
          <button class="icon-btn icon-btn-sm qz-edit" data-id="${z.id}" aria-label="${t('common.edit')}">${ic('pen','ic-s')}</button>
          <button class="btn btn-primary btn-sm qz-play" data-id="${z.id}">${ic('arrow','ic-s dir-flip')}<span>${t('study.start')}</span></button>
          <button class="icon-btn icon-btn-sm qz-del" data-id="${z.id}" aria-label="${t('common.delete')}">${ic('trash','ic-s')}</button>
        </div>`).join('')}</div>`
        :emptyState('check',t('study.noQuizzes'),t('study.noQuizzesHint'));
      $$('.qz-play',pane).forEach(b=>b.addEventListener('click',e=>{
        e.stopPropagation(); Nav.push('quizPlay',{quizId:b.dataset.id}); }));
      $$('.qz-edit',pane).forEach(b=>b.addEventListener('click',e=>{
        e.stopPropagation(); Nav.push('quizEdit',{quizId:b.dataset.id}); }));
      $$('.qz-del',pane).forEach(b=>b.addEventListener('click',e=>{ e.stopPropagation();
        confirmModal({title:t('common.delete'),msg:t('common.confirmDelete'),
          onOk:()=>{ state.quizzes=state.quizzes.filter(z=>z.id!==b.dataset.id); saveData(); drawQuizzes(); toast(t('toast.deleted')); } }); }));
      $$('[data-quiz]',pane).forEach(r=>{ r.addEventListener('click',()=>Nav.push('quizEdit',{quizId:r.dataset.quiz}));
        r.addEventListener('keydown',e=>{ if(e.key==='Enter') Nav.push('quizEdit',{quizId:r.dataset.quiz}); }); }); };
    const drawFocus=()=>{
      const C=2*Math.PI*54;
      const full=(Focus.phase==='focus'?Focus.mins:Focus.breakMins)*60||1;
      const off=C*(1-Math.min(1,Focus.remaining/full));
      pane.innerHTML=`
      <div class="focus-wrap card card-pad" data-phase="${Focus.phase}">
        <div class="focus-top">
          <span class="chip ${Focus.phase==='focus'?'chip-ok':''}">${t(Focus.phase==='focus'?'focus.session':'focus.break')}</span>
          <span class="chip mono">${t('focus.today')}: ${Focus.done()}</span>
        </div>
        <div class="focus-ring">
          <svg viewBox="0 0 120 120" width="216" height="216" aria-hidden="true">
            <circle class="fr-bg" cx="60" cy="60" r="54"/>
            <circle class="fr-fg" id="fr-arc" cx="60" cy="60" r="54"
              stroke-dasharray="${C.toFixed(1)}" stroke-dashoffset="${off.toFixed(1)}"/>
          </svg>
          <div class="focus-time mono" id="fr-time">${fmtMMSS(Focus.remaining)}</div>
        </div>
        <div class="focus-ctl">
          <button class="btn btn-primary" id="fo-go">${Focus.running?t('focus.pause'):t('focus.start')}</button>
          <button class="btn" id="fo-reset">${t('focus.reset')}</button>
        </div>
        <div style="width:100%">
          <div class="sect-h"><h2>${t('focus.length')}</h2></div>
          <div class="pillrow" id="fo-mins" style="justify-content:center">
            ${[15,25,50].map(m=>`<button class="pill ${Focus.mins===m?'on':''}" data-m="${m}" ${Focus.running?'disabled':''}>${m} ${t('focus.min')}</button>`).join('')}
          </div>
        </div>
      </div>`;
      const rr=()=>{ const tm=document.getElementById('fr-time'), arc=document.getElementById('fr-arc');
        if(!tm) return;
        const f2=(Focus.phase==='focus'?Focus.mins:Focus.breakMins)*60||1;
        tm.textContent=fmtMMSS(Focus.remaining);
        if(arc) arc.setAttribute('stroke-dashoffset',(C*(1-Math.min(1,Focus.remaining/f2))).toFixed(1)); };
      $('#fo-go',pane).addEventListener('click',()=>{
        Focus.running?Focus.pause():Focus.start(rr);
        Sound.play('click');
        $('#fo-go',pane).textContent=Focus.running?t('focus.pause'):t('focus.start'); rr(); });
      $('#fo-reset',pane).addEventListener('click',()=>{
        Focus.reset(); Sound.play('click');
        $('#fo-go',pane).textContent=t('focus.start'); rr(); });
      $('#fo-mins',pane).addEventListener('click',e=>{
        const b=e.target.closest('[data-m]'); if(!b||Focus.running) return;
        Focus.mins=+b.dataset.m; if(Focus.phase==='focus') Focus.reset();
        Sound.play('click'); drawFocus(); });
    };
    const draw=()=>{ addBtn.style.display=(tab==='focus')?'none':'';
      (tab==='cards'?drawDecks:tab==='quiz'?drawQuizzes:drawFocus)(); };
    $$('.seg button',sec).forEach(b=>b.addEventListener('click',()=>{
      tab=b.dataset.tab; studyTab=tab;
      $$('.seg button',sec).forEach(x=>{ x.classList.toggle('on',x===b);
        x.setAttribute('aria-selected',String(x===b)); }); draw(); }));
    addBtn.addEventListener('click',()=>{
      if(tab==='focus') return;
      if(tab==='cards') openDeckModal(null,()=>drawDecks());
      else Nav.push('quizEdit',{}); });
    draw();
    return sec;
  }
};
function openDeckModal(d,cb){
  openModal({title:d?t('common.edit'):t('study.newDeck'),
    body:field(t('study.deckName'),inp('dk-title','',d?d.title:'')),
    actions:[{label:t('common.cancel')},{label:t('common.save'),cls:'btn-primary',onClick:close=>{
      const v=$('#dk-title').value.trim(); if(!v) return;
      if(d) d.title=v.slice(0,80);
      else state.decks.push({id:uid(),title:v.slice(0,80),cards:[],createdAt:Date.now()});
      saveData(); close(); if(cb)cb(); toast(t('toast.saved')); }}]});
}
LAYERS.deck={
  title:p=>{ const d=state.decks.find(x=>x.id===p.deckId); return d?d.title:t('study.tabCards'); },
  render(p){
    const d=state.decks.find(x=>x.id===p.deckId);
    if(!d) return chrome({title:t('study.tabCards'),body:emptyState('layers',t('study.noDecks'))});
    if(!p.order||p.order.length!==d.cards.length) p.order=d.cards.map((_,i)=>i);
    let order=p.order, pos=0, flipped=false;
    const sec=chrome({title:d.title,
      actions:`<button class="icon-btn" id="dk-del" aria-label="${t('study.deleteDeck')}">${ic('trash')}</button>`,
      body:`
      <div id="dk-view"></div>
      <div class="sect-h" style="margin-top:22px"><h2>${t('study.tabCards')} · <span class="mono">${d.cards.length}</span></h2>
        <button class="btn btn-primary btn-sm" id="dk-add">${ic('plus','ic-s')}<span>${t('study.newCard')}</span></button></div>
      <ul class="list" id="dk-list"></ul>`});
    const view=$('#dk-view',sec), list=$('#dk-list',sec);
    const drawView=()=>{
      if(!d.cards.length){ view.innerHTML=emptyState('layers',t('study.noCards'),t('study.addFirst')); return; }
      if(pos>=order.length) pos=0;
      const c=d.cards[order[pos]];
      view.innerHTML=`
        <div class="flip3d ${flipped?'flipped':''}" id="flip" role="button" tabindex="0" aria-label="${t('study.flip')}">
          <div class="flip-in">
            <div class="flip-face front">${esc(c.front)}</div>
            <div class="flip-face back">${esc(c.back)}</div>
          </div>
        </div>
        <div class="flip-tools">
          <button class="icon-btn" id="c-prev" aria-label="${t('study.prev')}">${ic('chl','dir-flip')}</button>
          <span class="mono flip-count">${pos+1} / ${d.cards.length}</span>
          <button class="btn btn-sm" id="c-flip">${t('study.flip')}</button>
          <button class="icon-btn" id="c-shuf" aria-label="${t('study.shuffle')}">${ic('shuffle')}</button>
          <button class="icon-btn" id="c-next" aria-label="${t('study.next')}">${ic('chr','dir-flip')}</button>
        </div>`;
      const flip=$('#flip',view);
      const doFlip=()=>{ flipped=!flipped; flip.classList.toggle('flipped',flipped); };
      flip.addEventListener('click',doFlip);
      flip.addEventListener('keydown',e=>{ if(e.key==='Enter'||e.key===' '){ e.preventDefault(); doFlip(); } });
      $('#c-flip',view).addEventListener('click',doFlip);
      $('#c-prev',view).addEventListener('click',()=>{ pos=(pos-1+order.length)%order.length; flipped=false; drawView(); });
      $('#c-next',view).addEventListener('click',()=>{ pos=(pos+1)%order.length; flipped=false; drawView(); });
      $('#c-shuf',view).addEventListener('click',()=>{
        for(let i=order.length-1;i>0;i--){ const j=Math.floor(Math.random()*(i+1)); [order[i],order[j]]=[order[j],order[i]]; }
        p.order=order; pos=0; flipped=false; drawView(); });
      let sx=0,sy=0;
      flip.addEventListener('touchstart',e=>{ sx=e.touches[0].clientX; sy=e.touches[0].clientY; },{passive:true});
      flip.addEventListener('touchend',e=>{
        const dx=e.changedTouches[0].clientX-sx, dy=e.changedTouches[0].clientY-sy;
        if(Math.abs(dx)>60&&Math.abs(dx)>Math.abs(dy)*2){
          const rtl=document.documentElement.dir==='rtl';
          const next=rtl?dx<-60:dx>60;
          pos=next?(pos+1)%order.length:(pos-1+order.length)%order.length;
          flipped=false; drawView(); } },{passive:true});
    };
    const drawList=()=>{
      list.innerHTML=d.cards.map((c,i)=>`
        <li class="rowitem"><span class="row-main"><span class="row-title">${esc(c.front)}</span>
          <span class="row-sub">${esc(c.back)}</span></span>
          <button class="icon-btn icon-btn-sm dc-edit" data-i="${i}" aria-label="${t('common.edit')}">${ic('pen','ic-s')}</button>
          <button class="icon-btn icon-btn-sm dc-del" data-i="${i}" aria-label="${t('common.delete')}">${ic('trash','ic-s')}</button></li>`).join('');
      $$('.dc-edit',list).forEach(b=>b.addEventListener('click',()=>
        openCardModal(d.cards[+b.dataset.i],()=>{ drawList(); drawView(); })));
      $$('.dc-del',list).forEach(b=>b.addEventListener('click',()=>confirmModal({
        title:t('common.delete'),msg:t('common.confirmDelete'),
        onOk:()=>{ d.cards.splice(+b.dataset.i,1); p.order=d.cards.map((_,i)=>i); order=p.order; pos=0;
          saveData(); Nav.invalidate('study'); drawList(); drawView(); } }))); };
    $('#dk-add',sec).addEventListener('click',()=>openCardModal(null,card=>{
      d.cards.push(card); p.order=d.cards.map((_,i)=>i); order=p.order;
      pos=d.cards.length-1; flipped=false;
      saveData(); Nav.invalidate('study'); drawList(); drawView(); }));
    $('#dk-del',sec).addEventListener('click',()=>confirmModal({
      title:t('study.deleteDeck'),msg:t('study.deleteDeckMsg'),
      onOk:()=>{ state.decks=state.decks.filter(x=>x.id!==d.id); saveData();
        Nav.invalidate('study'); Nav.pop(); toast(t('toast.deleted')); } }));
    drawList(); drawView();
    return sec;
  }
};
function openCardModal(card,cb){
  openModal({title:card?t('study.editCard'):t('study.newCard'),
    body:`${field(t('study.front'),`<textarea class="input" id="cd-front" rows="2" placeholder="${t('study.frontPh')}">${esc(card?card.front:'')}</textarea>`)}
      ${field(t('study.back'),`<textarea class="input" id="cd-back" rows="2" placeholder="${t('study.backPh')}">${esc(card?card.back:'')}</textarea>`)}`,
    actions:[{label:t('common.cancel')},{label:t('common.save'),cls:'btn-primary',onClick:close=>{
      const f=$('#cd-front').value.trim(), b=$('#cd-back').value.trim();
      if(!f||!b) return;
      if(card){ card.front=f.slice(0,300); card.back=b.slice(0,300);
        saveData(); close(); if(cb)cb(); }
      else { close(); if(cb)cb({id:uid(),front:f.slice(0,300),back:b.slice(0,300)}); }
      toast(t('toast.saved')); }}]});
}
LAYERS.quizEdit={
  title:p=>{ const q=state.quizzes.find(x=>x.id===p.quizId); return q?q.title:t('study.newQuiz'); },
  onBeforePop(item){ const api=item.el._qe;
    if(api&&api.dirty){ openModal({ title:t('notes.discard'),
      body:`<p class="m-msg">${t('notes.discardMsg')}</p>`,
      actions:[{label:t('notes.discardBtn'),cls:'btn-danger',onClick:close=>{ close(); api.dirty=false; Nav.pop(); }},
        {label:t('common.cancel')}]}); return false; } return true; },
  render(p){
    const ex=p.quizId?state.quizzes.find(x=>x.id===p.quizId):null;
    const doc=ex?{title:ex.title,questions:ex.questions.map(q=>({q:q.q,options:q.options.slice(),correct:q.correct}))}
      :{title:'',questions:[{q:'',options:['','','',''],correct:0}]};
    const api={dirty:false};
    const sec=chrome({title:ex?t('study.editQuiz'):t('study.newQuiz'),
      actions:`<button class="btn btn-primary btn-sm" id="qz-save">${ic('check','ic-s')}<span>${t('common.save')}</span></button>`,
      body:`<div id="qz-build" style="max-width:640px"></div>`});
    const box=$('#qz-build',sec);
    const redraw=()=>{
      box.innerHTML=`
      ${field(t('study.quizName'),inp('qz-title','',doc.title))}
      ${doc.questions.map((q,i)=>`
        <div class="card card-pad q-card">
          <div class="sect-h"><h2 class="mono">${t('study.question')} ${i+1}</h2>
            <button class="icon-btn icon-btn-sm q-rm" data-i="${i}" aria-label="${t('study.removeQ')}">${ic('trash','ic-s')}</button></div>
          <textarea class="input" rows="2" data-q="${i}" placeholder="${t('study.questionPh')}">${esc(q.q)}</textarea>
          <div class="q-opts">
            ${q.options.map((o,j)=>`
            <label class="q-opt-row">
              <input type="radio" name="qc-${i}" data-i="${i}" data-j="${j}" ${q.correct===j?'checked':''} aria-label="${t('study.correctOpt')}">
              <input class="input" data-o="${i}-${j}" value="${esc(o)}" placeholder="${t('study.option',{n:j+1})}">
            </label>`).join('')}
          </div>
        </div>`).join('')}
      <button class="btn" id="qz-addq" style="margin-top:4px">${ic('plus','ic-s')}<span>${t('study.addQuestion')}</span></button>`;
      $('#qz-title',box).addEventListener('input',e=>{ doc.title=e.target.value; api.dirty=true; });
      $$('[data-q]',box).forEach(el=>el.addEventListener('input',()=>{ doc.questions[+el.dataset.q].q=el.value; api.dirty=true; }));
      $$('[data-o]',box).forEach(el=>el.addEventListener('input',()=>{
        const [i,j]=el.dataset.o.split('-').map(Number);
        doc.questions[i].options[j]=el.value; api.dirty=true; }));
      $$('input[type=radio]',box).forEach(el=>el.addEventListener('change',()=>{
        doc.questions[+el.dataset.i].correct=+el.dataset.j; api.dirty=true; }));
      $('#qz-addq',box).addEventListener('click',()=>{
        doc.questions.push({q:'',options:['','','',''],correct:0}); api.dirty=true; redraw(); });
      $$('.q-rm',box).forEach(b=>b.addEventListener('click',()=>{
        if(doc.questions.length===1){ toast(t('study.minQ'),'err'); return; }
        doc.questions.splice(+b.dataset.i,1); api.dirty=true; redraw(); })); };
    redraw();
    $('#qz-save',sec).addEventListener('click',()=>{
      doc.title=$('#qz-title',box).value.trim();
      if(!doc.title){ toast(t('study.needTitle'),'err'); return; }
      for(let i=0;i<doc.questions.length;i++){ const q=doc.questions[i];
        const filled=q.options.map(o=>o.trim()).filter(Boolean);
        if(!q.q.trim()||filled.length<2||!q.options[q.correct].trim()){
          toast(t('study.needQ',{n:i+1}),'err'); return; } }
      const clean={ id:ex?ex.id:uid(), title:doc.title.slice(0,80),
        createdAt:ex?ex.createdAt:Date.now(),
        questions:doc.questions.map(q=>({q:q.q.trim().slice(0,400),
          options:q.options.map(o=>o.trim().slice(0,160)),correct:q.correct})) };
      const ix=state.quizzes.findIndex(x=>x.id===clean.id);
      if(ix>-1) state.quizzes[ix]=clean; else state.quizzes.push(clean);
      api.dirty=false; saveData(); Nav.invalidate('study');
      toast(t('toast.saved')); Nav.pop(); });
    sec._qe=api;
    return sec;
  }
};
LAYERS.quizPlay={
  title:p=>{ const q=state.quizzes.find(x=>x.id===p.quizId); return q?q.title:t('study.tabQuiz'); },
  render(p){
    const quiz=state.quizzes.find(x=>x.id===p.quizId);
    if(!quiz||!quiz.questions.length)
      return chrome({title:t('study.tabQuiz'),body:emptyState('check',t('study.noQuizzes'))});
    let i=0; const answers=new Array(quiz.questions.length).fill(-1);
    const sec=chrome({title:quiz.title,body:`<div id="qp" style="max-width:640px"></div>`});
    const box=$('#qp',sec);
    const drawQ=()=>{
      const q=quiz.questions[i];
      box.innerHTML=`
      <div class="qp-prog"><div class="progress"><i style="width:${(i/quiz.questions.length)*100}%"></i></div>
        <span class="qp-count mono">${t('study.qOf',{i:i+1,n:quiz.questions.length})}</span></div>
      <div class="card card-pad" style="margin-top:14px"><p class="qp-q">${esc(q.q)}</p></div>
      <div class="list" style="margin-top:14px">${q.options.map((o,j)=>o?`
        <button class="opt ${answers[i]===j?'sel':''}" data-j="${j}">
          <span class="opt-key mono">${String.fromCharCode(65+j)}</span><span>${esc(o)}</span></button>`:'').join('')}</div>
      <div class="qp-foot"><button class="btn btn-primary" id="qp-next" ${answers[i]<0?'disabled':''}>
        ${i===quiz.questions.length-1?t('study.finish'):t('study.nextQ')} ${ic('arrow','ic-s dir-flip')}</button></div>`;
      $$('.opt',box).forEach(b=>b.addEventListener('click',()=>{ answers[i]=+b.dataset.j; drawQ(); }));
      $('#qp-next',box).addEventListener('click',()=>{
        if(answers[i]<0){ toast(t('study.selectFirst'),'err'); return; }
        i++; i<quiz.questions.length?drawQ():drawRes(); }); };
    const drawRes=()=>{
      const correct=answers.filter((a,k)=>a===quiz.questions[k].correct).length;
      const pctN=Math.round(correct/quiz.questions.length*100);
      if(pctN>=80) FX.confetti();
      box.innerHTML=`
      <div class="card card-pad res-hero"><span class="res-num mono">${pctN}%</span>
        <span class="res-lbl">${t('study.score')} · ${correct} / ${quiz.questions.length}</span></div>
      <div class="sect-h" style="margin-top:20px"><h2>${t('study.review')}</h2></div>
      <div>${quiz.questions.map((q,k)=>{ const ok=answers[k]===q.correct; return `
        <div class="card card-pad rv ${ok?'rv-ok':'rv-no'}">
          <p class="rv-q">${esc(q.q)}</p>
          <p class="rv-line">${t('study.your')}: <span class="chip ${ok?'chip-ok':'chip-no'}">${esc(q.options[answers[k]]||'—')}</span></p>
          ${ok?'':`<p class="rv-line">${t('study.correctAns')}: <span class="chip chip-ok">${esc(q.options[q.correct])}</span></p>`}
        </div>`;}).join('')}</div>
      <div class="qp-foot">
        <button class="btn" id="qp-done">${t('common.done')}</button>
        <button class="btn btn-primary" id="qp-retry">${ic('refresh','ic-s')}<span>${t('study.retry')}</span></button></div>`;
      $('#qp-done',box).addEventListener('click',()=>Nav.pop());
      $('#qp-retry',box).addEventListener('click',()=>{ i=0; answers.fill(-1); drawQ(); }); };
    drawQ();
    return sec;
  }
};

/* ── SETTINGS ─────────────────────────────────────────────── */
LAYERS.settings={
  title:()=>t('nav.settings'),
  render(){
    const s=state.settings;
    const body=`
    <div class="set-group card card-pad">
      <div class="set-h">${ic('globe')}<div><h2>${t('set.language')}</h2><p>${t('set.langDesc')}</p></div></div>
      <div class="pillrow" id="set-lang">
        <button class="pill ${s.lang!=='ar'?'on':''}" data-l="en">English</button>
        <button class="pill ${s.lang==='ar'?'on':''}" data-l="ar">العربية</button>
      </div>
    </div>
    <div class="set-group card card-pad">
      <div class="set-h">${ic('user')}<div><h2>${t('set.profile')}</h2><p>${t('set.usernameDesc')}</p></div></div>
      <div class="inline-form">
        <input class="input" id="set-name" value="${esc(state.user.name)}" maxlength="40" placeholder="${t('set.usernamePh')}" aria-label="${t('set.username')}">
        <button class="btn btn-primary" id="set-name-save">${t('common.save')}</button>
      </div>
    </div>
    <div class="set-group card card-pad">
      <div class="set-h">${(s.theme==='dark'||s.theme==='oled')?ic('moon'):ic('sun')}<div><h2>${t('set.appearance')}</h2><p>${t('set.theme')}</p></div></div>
      <div class="swatches" id="set-theme" role="radiogroup" aria-label="${t('set.theme')}">
        ${[['dark','#0A1024','#22D3EE|#3B82F6|#8B5CF6'],['oled','#000000','#22D3EE|#3B82F6|#8B5CF6'],
           ['light','#F7F9FF','#0891B2|#2563EB|#7C3AED'],['paper','#FBF6EC','#0F766E|#B45309|#9F1239'],
           ['sage','#F7FBF7','#0D9488|#047857|#4338CA'],['rose','#FDF5F8','#DB2777|#9333EA|#BE185D']]
          .map(([id,bg,dots])=>{ const d=dots.split('|'); return `
          <button class="swatch ${s.theme===id?'on':''}" data-th="${id}" role="radio" aria-checked="${s.theme===id}">
            <span class="sw-prev" style="background:${bg}">
              <i style="background:${d[0]}"></i><i style="background:${d[1]}"></i><i style="background:${d[2]}"></i>
            </span>
            <span class="sw-name">${t('set.th.'+id)}</span>
          </button>`;}).join('')}
      </div>
    </div>
    <div class="set-group card card-pad">
      <div class="set-h">${ic('img')}<div><h2>${t('set.bg')}</h2><p>${t('set.bgDesc')}</p></div></div>
      <div class="bg-cells">
        ${THEMES.map(th=>`
        <div class="bg-cell">
          <span class="bg-prev" style="background:${THEME_META[th]||'#050816'}"></span>
          <span class="bg-name">${t('set.th.'+th)}</span>
          <div class="bg-acts">
            <button class="btn btn-sm bg-up" data-th="${th}">${ic('ul','ic-s')}<span>${t('set.bgUp')}</span></button>
            <button class="icon-btn icon-btn-sm bg-rm" data-th="${th}" aria-label="${t('set.bgRm')}" title="${t('set.bgRm')}">${ic('x','ic-s')}</button>
          </div>
        </div>`).join('')}
      </div>
      <input type="file" id="bg-file" accept="image/*" hidden>
    </div>
    <div class="set-group card card-pad set-row">
      <div class="set-h">${ic('vol')}<div><h2>${t('set.sound')}</h2><p>${t('set.soundDesc')}</p></div></div>
      <button class="switch ${s.sound?'on':''}" id="set-sound" role="switch" aria-checked="${s.sound}" aria-label="${t('set.sound')}"></button>
    </div>
    <div class="set-group card card-pad">
      <div class="set-h">${ic('dl')}<div><h2>${t('set.backup')}</h2><p>${t('set.backupDesc')}</p></div></div>
      <div class="pillrow">
        <button class="btn" id="set-export">${ic('dl','ic-s')}<span>${t('set.export')}</span></button>
        <button class="btn" id="set-import">${ic('ul','ic-s')}<span>${t('set.import')}</span></button>
      </div>
      <input type="file" id="set-file" accept="application/json,.json" hidden>
    </div>
    <div class="set-group card card-pad">
      <div class="set-h">${ic('alert')}<div><h2>${t('set.danger')}</h2><p>${t('set.clearMsg')}</p></div></div>
      <button class="btn btn-danger" id="set-wipe">${ic('trash','ic-s')}<span>${t('set.clearAll')}</span></button>
    </div>
    <div class="set-group card card-pad about">
      ${ic('logo','about-logo')}
      <div><h2>UBAD ACADEMY HUB</h2><p>${t('set.aboutBody')}</p>
        <p class="mono" style="font-size:11px;color:var(--ink3);margin-top:6px">${t('set.version')} 1.0.0</p></div>
    </div>`;
    const sec=chrome({title:t('nav.settings'),body});
    $('#set-lang',sec).addEventListener('click',e=>{
      const b=e.target.closest('[data-l]'); if(b) setLang(b.dataset.l); });
    $('#set-theme',sec).addEventListener('click',e=>{
      const b=e.target.closest('[data-th]'); if(b) setTheme(b.dataset.th); });
    let pendBg='dark';
    $$('.bg-up',sec).forEach(b=>b.addEventListener('click',()=>{
      pendBg=b.dataset.th; $('#bg-file',sec).click(); }));
    $('#bg-file',sec).addEventListener('change',e=>{
      const f=e.target.files[0]; e.target.value='';
      if(f) uploadBg(pendBg,f); });
    $$('.bg-rm',sec).forEach(b=>b.addEventListener('click',()=>removeBg(b.dataset.th)));
    $('#set-name-save',sec).addEventListener('click',()=>{
      const v=$('#set-name',sec).value.trim();
      if(!v){ toast(t('set.needName'),'err'); return; }
      state.user.name=v.slice(0,40); saveData(); toast(t('set.nameSaved')); });
    $('#set-sound',sec).addEventListener('click',()=>{
      state.settings.sound=!state.settings.sound; saveData();
      const sw=$('#set-sound',sec);
      sw.classList.toggle('on',state.settings.sound);
      sw.setAttribute('aria-checked',String(state.settings.sound));
      if(state.settings.sound) Sound.play('click'); });
    $('#set-export',sec).addEventListener('click',exportBackup);
    $('#set-import',sec).addEventListener('click',()=>$('#set-file',sec).click());
    $('#set-file',sec).addEventListener('change',e=>{
      const f=e.target.files[0]; e.target.value=''; if(f) importBackup(f); });
    $('#set-wipe',sec).addEventListener('click',()=>confirmModal({
      title:t('set.clearAll'),msg:t('set.clearMsg'),okLabel:t('set.clearAll'),onOk:wipeAll }));
    return sec;
  }
};
function applyLang(){ const l=state.settings.lang==='ar'?'ar':'en';
  document.documentElement.lang=l; document.documentElement.dir=l==='ar'?'rtl':'ltr'; }
const THEMES=['dark','oled','light','paper','sage','rose'];
const THEME_META={'dark':'#050816','oled':'#000000','light':'#EDF1FB',
  'paper':'#F6EFE6','sage':'#EDF5EF','rose':'#F9EFF2'};
function applyTheme(){ const th=THEMES.includes(state.settings.theme)?state.settings.theme:'dark';
  state.settings.theme=th;
  document.documentElement.dataset.theme=th;
  const m=document.querySelector('meta[name=theme-color]');
  if(m) m.content=THEME_META[th]||'#050816';
  try{ bgApply(); }catch(e){} }
function setLang(l){ state.settings.lang=l; saveData(); applyLang();
  Sound.play('transition'); Nav.rerenderAll(); }
function setTheme(th){ state.settings.theme=th; saveData(); applyTheme(); Nav.rerenderAll(); }
async function wipeAll(){
  try{ await DB.clear('kv'); await DB.clear('notes'); }catch(e){}
  for(const th of THEMES){ try{ await DB.del('kv','bg-'+th); }catch(e){} }
  state.user={name:'Ubad'}; Object.assign(state.settings,{lang:'en',sound:true,theme:'dark'});
  state.courses=[]; state.notes=[]; state.events=[]; state.tasks=[]; state.decks=[]; state.quizzes=[];
  state.focus={day:'',done:0}; state.islam=normIslam({});
  try{ localStorage.removeItem(PREF_KEY); }catch(e){}
  applyLang(); applyTheme(); bgApply(); Nav.popTo(0,true); Nav.rerenderAll(); toast(t('set.cleared'));
}

/* ═══ 12. search overlay (compact icon → popup) ══════════════ */
let searchOpen=false;
function openSearch(){
  if(searchOpen) return; searchOpen=true; Sound.play('click');
  const wrap=document.createElement('div'); wrap.className='search-wrap';
  wrap.innerHTML=`
  <div class="search-panel" role="dialog" aria-modal="true" aria-label="${t('common.search')}">
    <div class="search-bar">${ic('search')}
      <input class="s-input" id="s-q" placeholder="${t('search.ph')}" autocomplete="off" aria-label="${t('common.search')}">
      <button class="icon-btn" id="s-x" aria-label="${t('common.close')}">${ic('x')}</button></div>
    <div class="search-res" id="s-res"></div>
  </div>`;
  $('#overlay-root').appendChild(wrap);
  const input=$('#s-q',wrap), res=$('#s-res',wrap);
  const actions=[];
  const close=()=>{ if(!searchOpen) return; searchOpen=false; wrap.remove(); };
  $('#s-x',wrap).addEventListener('click',close);
  wrap.addEventListener('pointerdown',e=>{ if(e.target===wrap) close(); });
  wrap.addEventListener('keydown',e=>{
    if(e.key==='Escape') close();
    if(e.key==='Enter'){ const f=$('.sr-item',res); if(f) f.click(); } });
  input.focus();
  const render=()=>{ const raw=input.value.trim(); const q=raw.toLowerCase();
    res.innerHTML=raw?buildResults(q,raw,actions):''; };
  input.addEventListener('input',render);
  res.addEventListener('click',e=>{
    const item=e.target.closest('.sr-item'); if(!item) return;
    const fn=actions[+item.dataset.act]; close();
    if(fn){ try{ fn(); }catch(err){} } });
}
function buildResults(q,raw,actions){
  actions.length=0;
  const push=(fn,iconT,title,sub)=>{ actions.push(fn);
    return `<button class="sr-item" data-act="${actions.length-1}">
      <span class="sr-badge">${ic(iconT,'ic-s')}</span>
      <span class="sr-main"><span class="sr-title">${esc(title)}</span>${sub?`<span class="sr-sub">${esc(sub)}</span>`:''}</span>
      ${ic('chr','ic-s dir-flip')}</button>`; };
  let html=''; let any=false;
  const grp=(label,inner)=>{ if(inner){ any=true; html+=`<div class="sr-group">${esc(label)}</div>${inner}`; } };
  grp(t('search.notes'),
    state.notes.filter(n=>(n.title+' '+n.body+' '+n.tags.join(' ')).toLowerCase().includes(q)).slice(0,5)
      .map(n=>push(()=>Nav.push('noteEditor',{id:n.id}),'note',
        n.title||t('notes.untitled'),n.body.slice(0,60))).join(''));
  grp(t('search.courses'),
    state.courses.filter(c=>(c.name+' '+c.code+' '+(c.instructor||'')).toLowerCase().includes(q)).slice(0,5)
      .map(c=>push(()=>Nav.push('courseDetail',{id:c.id}),'book',
        c.name,[c.code,c.instructor].filter(Boolean).join(' · '))).join(''));
  grp(t('search.events'),
    state.events.filter(e=>(e.title+' '+(e.desc||'')).toLowerCase().includes(q)).slice(0,5)
      .map(e=>push(()=>Nav.push('calendar',{date:e.date}),'cal',
        e.title,fmtDate(parseYmd(e.date),{day:'numeric',month:'long'}))).join(''));
  grp(t('search.decks'),
    state.decks.filter(d=>(d.title+' '+d.cards.map(c=>c.front+' '+c.back).join(' ')).toLowerCase().includes(q)).slice(0,4)
      .map(d=>push(()=>Nav.push('deck',{deckId:d.id}),'layers',
        d.title,d.cards.length+' '+t('study.cardsLc'))).join(''));
  grp(t('search.quizzes'),
    state.quizzes.filter(z=>(z.title+' '+z.questions.map(x=>x.q).join(' ')).toLowerCase().includes(q)).slice(0,4)
      .map(z=>push(()=>Nav.push('quizPlay',{quizId:z.id}),'check',
        z.title,z.questions.length+' '+t('study.questionsLc'))).join(''));
  return any?html
    :`<div class="empty empty-sm">${ic('search')}<p>${esc(t('search.none',{q:raw}))}</p></div>`;
}

/* ═══ 13. backup / restore (JSON + base64-safe attachments) ══ */
async function exportBackup(){
  try{
    const notes=await Promise.all(state.notes.map(async n=>({
      id:n.id, title:n.title, body:n.body, tags:n.tags, pin:!!n.pin,
      createdAt:n.createdAt, updatedAt:n.updatedAt,
      images:await Promise.all(n.images.map(async a=>({name:a.name,type:a.blob.type,data:await blobToDataURL(a.blob)}))),
      audio:await Promise.all(n.audio.map(async a=>({name:a.name,type:a.blob.type,data:await blobToDataURL(a.blob)})))
    })));
    const backgrounds={};
    for(const th of THEMES){ try{ const r=await DB.get('kv','bg-'+th);
      if(r&&r.blob instanceof Blob) backgrounds[th]={type:r.blob.type,data:await blobToDataURL(r.blob)}; }catch(e){} }
    const payload={ app:'ubad-academy-hub', version:1, exportedAt:new Date().toISOString(),
      data:{ user:state.user, settings:state.settings,
        courses:state.courses, events:state.events, tasks:state.tasks,
        decks:state.decks, quizzes:state.quizzes, focus:state.focus,
        islam:state.islam, notes, backgrounds } };
    const blob=new Blob([JSON.stringify(payload)],{type:'application/json'});
    const a=document.createElement('a');
    a.href=URL.createObjectURL(blob);
    a.download='ubad-backup-'+today()+'.json';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(()=>URL.revokeObjectURL(a.href),4000);
    toast(t('set.exported'));
  }catch(e){ toast(t('toast.error'),'err'); }
}
async function importBackup(file){
  let parsed;
  try{ parsed=JSON.parse(await file.text()); }
  catch(e){ toast(t('set.importFailed'),'err'); return; }
  const d=parsed&&parsed.data;
  if(!parsed||parsed.app!=='ubad-academy-hub'||!d||typeof d!=='object'){
    toast(t('set.importFailed'),'err'); return; }
  confirmModal({ title:t('set.import'), msg:t('set.importConfirm'), okLabel:t('set.import'),
    onOk:async()=>{
      try{
        const dataToBlob=async a=>{ try{
          if(!a||typeof a.data!=='string'||!a.data.startsWith('data:')) return null;
          const r=await fetch(a.data); const b=await r.blob();
          return { name:normStr(a.name,80,'file'), blob:b };
        }catch(err){ return null; } };
        const notes=await Promise.all(normArr(d.notes).map(async n=>{
          const imgs=(await Promise.all(normArr(n&&n.images).map(dataToBlob))).filter(Boolean);
          const auds=(await Promise.all(normArr(n&&n.audio).map(dataToBlob))).filter(Boolean);
          return { id:normStr(n&&n.id,40)||uid(), title:normStr(n&&n.title,120),
            body:normStr(n&&n.body,20000),
            tags:normArr(n&&n.tags).map(x=>normStr(x,24)).slice(0,8),
            createdAt:clampNum(n&&n.createdAt,0,1e15,Date.now()),
            updatedAt:clampNum(n&&n.updatedAt,0,1e15,Date.now()),
            pin:!!(n&&n.pin),
            images:imgs, audio:auds }; }));
        try{ await DB.clear('notes'); }catch(e){}
        for(const rec of notes){ try{ await DB.put('notes',rec); }catch(e){} }
        state.notes=notes.sort((a,b)=>(b.pin?1:0)-(a.pin?1:0)||b.updatedAt-a.updatedAt);
        /* الخلفيات المخصصة */
        const bgs=(d.backgrounds&&typeof d.backgrounds==='object')?d.backgrounds:{};
        for(const th of THEMES){ const b=bgs[th];
          if(b&&typeof b.data==='string'&&b.data.startsWith('data:')){
            try{ const bl=await (await fetch(b.data)).blob();
              await DB.put('kv',{id:'bg-'+th,blob:bl}); }catch(e){} } }
        hydrate({ user:d.user, settings:d.settings, courses:d.courses,
          events:d.events, tasks:d.tasks, decks:d.decks, quizzes:d.quizzes,
          focus:d.focus, islam:d.islam });
        saveData(); applyLang(); applyTheme(); bgApply();
        Nav.popTo(0,true); Nav.rerenderAll();
        toast(t('set.imported'));
      }catch(err){ toast(t('set.importFailed'),'err'); }
    }});
}

/* ═══ 14. boot — single, ordered initialization ══════════════ */
async function boot(){
  loadPrefs();            /* 1-2. preferences + storage flags */
  applyLang();            /* 3. language before first paint of layers */
  applyTheme();           /* 4. theme (+ prepares bg layer element) */
  bindParallax();         /* 5. pointer engines */
  bindTilt();
  bindEdgeBack();
  bindKeys();
  try{ await DB.open(); }catch(e){}          /* 6. IndexedDB (memory fallback ok) */
  try{ await Promise.all([loadData(),loadNotes()]); }catch(e){} /* 7. user data */
  try{ await bgApply(); }catch(e){}          /* 7.5 custom theme background */
  Sound.init();           /* 8. audio manager (silent until gesture) */
  Hist.init();            /* 8.5 native back bridge */
  Nav.init('hub');        /* 9. render Main Hub */
}
boot().catch(()=>{ /* last-resort: never leave a blank screen */
  const s=document.getElementById('stage');
  if(s&&!s.childElementCount){
    const el=document.createElement('section'); el.className='layer';
    el.innerHTML=`<div class="lbody"><div class="wrap">${emptyState('alert','UBAD ACADEMY HUB','')}</div></div>`;
    s.appendChild(el); }
});

})();