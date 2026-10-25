/**
 * Text-only meal cases for the meal agent. Each case says which table entries are acceptable for each
 * food, and gives a reference meal (entry ids × the grams assumed for a typical Indian portion) from
 * which the accepted calorie range is computed at run time, so ranges follow data/food-db.json.
 *
 * Accept patterns: an exact id, "components" (a dish the agent built from matched ingredients),
 * "fdc:" / "off:" prefixes (any USDA / Open Food Facts entry), or a RegExp over the id.
 */

export type Accept = string | RegExp;

export interface ExpectedItem {
  label: string;
  accept: Accept[];
}

export interface RefPart {
  /** An id in data/food-db.json, or a USDA id (fdc:…) with its kcal per 100 g given in [kcalPer100g]. */
  id: string;
  grams: number;
  kcalPer100g?: number;
}

export interface EvalCase {
  id: string;
  text: string;
  mealType: "breakfast" | "lunch" | "dinner" | "snack";
  expect: ExpectedItem[];
  /** Reference portion for the calorie range. */
  ref: RefPart[];
  /** kcal of reference parts no table holds (chutney, gravy base), stated in the comment. */
  extraKcal?: number;
  /** Relative half-width of the kcal range; 0.25 by default, wider where portions vary a lot. */
  tolerance?: number;
  /** The agent should find no food at all. */
  expectEmpty?: boolean;
}

// Shared accept lists.
const ROTI: Accept[] = ["cofid:11-459", "cofid:11-987", /^fdc:/, "components"];
const RICE: Accept[] = [/^cofid:11-8[5-8]\d$/, /^ifct:A01[45]$/, /^fdc:/, "components"];
const DAL: Accept[] = [/^ifct:B0(01|03|10|13|21)$/, /^cofid:13-(05\d|06\d|10[2-5])$/, /^cofid:15-(7[4-8]\d|641)$/, /^cofid:13-484$/, /^fdc:/, "components"];
const CURD: Accept[] = [/^cofid:12-(18\d|37\d|5\d\d)$/, "inpf:805", /^fdc:/, "components"];
const ANY_DISH: Accept[] = [/^fdc:/, "components"];

export const CASES: EvalCase[] = [
  // ---- Indian home meals ----
  {
    id: "roti-toor-dal",
    text: "2 rotis and a bowl of toor dal",
    mealType: "lunch",
    expect: [
      { label: "roti", accept: ROTI },
      { label: "toor dal", accept: [/^ifct:B021$/, /^cofid:15-77\d$/, /^cofid:13-10[45]$/, /^fdc:/, "components"] },
    ],
    // 2 rotis × 40 g; a 200 g bowl ≈ 60 g raw toor dal + 5 g ghee.
    ref: [{ id: "cofid:11-459", grams: 80 }, { id: "ifct:B021", grams: 60 }, { id: "ifct:T013", grams: 5 }],
  },
  {
    id: "hinglish-roti-dal",
    text: "2 roti aur dal",
    mealType: "dinner",
    expect: [{ label: "roti", accept: ROTI }, { label: "dal", accept: DAL }],
    // Same reference as above: unnamed dal ≈ 60 g raw dal + 5 g ghee in a 200 g bowl.
    ref: [{ id: "cofid:11-459", grams: 80 }, { id: "ifct:B013", grams: 60 }, { id: "ifct:T013", grams: 5 }],
    tolerance: 0.3,
  },
  {
    id: "rajma-chawal",
    text: "a plate of rajma chawal",
    mealType: "lunch",
    expect: [
      { label: "rajma", accept: [/^ifct:B0(18|19|20)$/, "inpf:697", /^cofid:13-1\d\d$/, "cofid:15-859", /^fdc:/, "components"] },
      { label: "rice", accept: RICE },
    ],
    // 200 g rajma curry ≈ 50 g raw rajma + 7 g oil; 200 g boiled rice.
    ref: [{ id: "ifct:B020", grams: 50 }, { id: "ifct:T005", grams: 7 }, { id: "cofid:11-858", grams: 200 }],
    tolerance: 0.3,
  },
  {
    id: "palak-paneer-roti",
    text: "palak paneer with 2 rotis",
    mealType: "dinner",
    expect: [{ label: "palak paneer", accept: ANY_DISH }, { label: "roti", accept: ROTI }],
    // 200 g palak paneer ≈ 150 g spinach + 70 g paneer + 10 g ghee; 2 rotis × 40 g.
    ref: [{ id: "ifct:C033", grams: 150 }, { id: "ifct:L003", grams: 70 }, { id: "ifct:T013", grams: 10 }, { id: "cofid:11-459", grams: 80 }],
    tolerance: 0.35,
  },
  {
    id: "bhindi-roti",
    text: "bhindi sabzi and 2 chapatis",
    mealType: "lunch",
    expect: [{ label: "bhindi", accept: [/^cofid:15-(68[456]|707|82[45])$/, ...ANY_DISH] }, { label: "chapati", accept: ROTI }],
    // 150 g okra bhaji; 2 × 40 g chapati.
    ref: [{ id: "cofid:15-685", grams: 150 }, { id: "cofid:11-459", grams: 80 }],
    tolerance: 0.3,
  },
  {
    id: "aloo-gobi-roti",
    text: "aloo gobi with 2 rotis",
    mealType: "dinner",
    expect: [{ label: "aloo gobi", accept: [/^cofid:15-(679|741|02[12]|040)$/, ...ANY_DISH] }, { label: "roti", accept: ROTI }],
    // 150 g cauliflower and potato bhaji; 2 × 40 g roti.
    ref: [{ id: "cofid:15-679", grams: 150 }, { id: "cofid:11-459", grams: 80 }],
    tolerance: 0.3,
  },
  {
    id: "khichdi-ghee",
    text: "one bowl of khichdi with ghee",
    mealType: "dinner",
    expect: [{ label: "khichdi", accept: [/^cofid:15-52[89]$/, ...ANY_DISH] }],
    // 250 g bowl.
    ref: [{ id: "cofid:15-528", grams: 250 }],
    tolerance: 0.3,
  },
  {
    id: "curd-rice",
    text: "curd rice, one plate",
    mealType: "lunch",
    expect: [{ label: "curd rice", accept: [...RICE, ...CURD] }],
    // 150 g boiled rice + 100 g curd.
    ref: [{ id: "cofid:11-858", grams: 150 }, { id: "cofid:12-184", grams: 100 }],
    tolerance: 0.35,
  },
  {
    id: "aloo-paratha-curd",
    text: "2 aloo parathas with a small bowl of dahi",
    mealType: "breakfast",
    expect: [{ label: "aloo paratha", accept: ["cofid:11-1104", "cofid:11-912", ...ANY_DISH] }, { label: "curd", accept: CURD }],
    // 2 parathas × 90 g; 100 g curd.
    ref: [{ id: "cofid:11-1104", grams: 180 }, { id: "cofid:12-184", grams: 100 }],
    tolerance: 0.35,
  },
  {
    id: "poha",
    text: "a plate of poha with peanuts",
    mealType: "breakfast",
    expect: [{ label: "poha", accept: ["inpf:639", "ifct:A011", ...ANY_DISH] }],
    // 200 g cooked poha ≈ 60 g rice flakes + 10 g oil + 15 g peanuts (counted as 85 kcal) + onion.
    ref: [{ id: "ifct:A011", grams: 60 }, { id: "ifct:T005", grams: 10 }, { id: "ifct:G017", grams: 30 }],
    extraKcal: 85,
    tolerance: 0.35,
  },
  {
    id: "upma",
    text: "rava upma, one bowl",
    mealType: "breakfast",
    expect: [{ label: "upma", accept: ["inpf:636", "ifct:A022", ...ANY_DISH] }],
    // 200 g upma ≈ 50 g semolina + 10 g oil + vegetables (30 kcal).
    ref: [{ id: "ifct:A022", grams: 50 }, { id: "ifct:T005", grams: 10 }],
    extraKcal: 30,
    tolerance: 0.35,
  },
  {
    id: "egg-bhurji-hinglish",
    text: "subah 2 anda bhurji aur 2 toast",
    mealType: "breakfast",
    expect: [
      { label: "egg bhurji", accept: [/^ifct:M00\d$/, /^cofid:12-9\d\d$/, ...ANY_DISH] },
      { label: "toast", accept: [/^cofid:11-(1001|1136|1004|982|1145|97\d)$/, ...ANY_DISH] },
    ],
    // 2 eggs (100 g) + 5 g oil; 2 slices toast × 30 g.
    ref: [{ id: "ifct:M004", grams: 100 }, { id: "ifct:T005", grams: 5 }, { id: "cofid:11-1001", grams: 60 }],
    tolerance: 0.3,
  },
  {
    id: "boiled-eggs-toast",
    text: "2 boiled eggs and 2 slices of white bread toast",
    mealType: "breakfast",
    expect: [
      { label: "boiled eggs", accept: [/^ifct:M00[49]$/, /^cofid:12-9\d\d$/, /^fdc:/] },
      { label: "toast", accept: [/^cofid:11-(1001|1145|97\d)$/, /^fdc:/] },
    ],
    // 2 eggs × 50 g; 2 slices × 30 g.
    ref: [{ id: "ifct:M004", grams: 100 }, { id: "cofid:11-1001", grams: 60 }],
  },
  {
    id: "masala-chai-parle-g",
    text: "a cup of masala chai with 2 Parle-G biscuits",
    mealType: "snack",
    expect: [
      { label: "chai", accept: [/^cofid:17-16\d$/, /^ifct:L00\d$/, ...ANY_DISH] },
      { label: "Parle-G", accept: ["inpf:65", /^off:/, /^fdc:/] },
    ],
    // 150 ml chai ≈ 50 g whole milk + 10 g sugar; 2 Parle-G × 6 g.
    ref: [{ id: "ifct:L002", grams: 50 }, { id: "cofid:17-063", grams: 10 }, { id: "inpf:65", grams: 12 }],
    tolerance: 0.4,
  },
  {
    id: "lassi",
    text: "a glass of sweet lassi",
    mealType: "snack",
    expect: [{ label: "lassi", accept: ["cofid:12-373", ...ANY_DISH] }],
    // 250 ml.
    ref: [{ id: "cofid:12-373", grams: 250 }],
    tolerance: 0.35,
  },
  {
    id: "banana",
    text: "a banana",
    mealType: "snack",
    expect: [{ label: "banana", accept: [/^ifct:E0(09|1[0-2])$/, /^cofid:14-\d+$/, /^fdc:/] }],
    // One medium banana, 110 g edible.
    ref: [{ id: "ifct:E012", grams: 110 }],
    tolerance: 0.3,
  },
  {
    id: "apple",
    text: "one apple",
    mealType: "snack",
    expect: [{ label: "apple", accept: [/^ifct:E00[1-4]$/, /^cofid:14-\d+$/, /^fdc:/] }],
    // One medium apple, 170 g edible.
    ref: [{ id: "ifct:E001", grams: 170 }],
    tolerance: 0.3,
  },
  // ---- Indian restaurant and street food ----
  {
    id: "chicken-biryani",
    text: "a plate of chicken biryani",
    mealType: "dinner",
    expect: [{ label: "biryani", accept: ["cofid:19-454", ...ANY_DISH] }],
    // Restaurant plate, 350 g.
    ref: [{ id: "cofid:19-454", grams: 350 }],
    tolerance: 0.35,
  },
  {
    id: "paneer-butter-masala-naan",
    text: "paneer butter masala with 2 butter naan",
    mealType: "dinner",
    expect: [
      { label: "paneer butter masala", accept: ["inpf:644", ...ANY_DISH] },
      { label: "naan", accept: [/^cofid:11-9(10|73)$/, ...ANY_DISH] },
    ],
    // 200 g curry ≈ 80 g paneer + 15 g ghee/butter + tomato-cream gravy (100 kcal); 2 naan × 90 g.
    ref: [{ id: "ifct:L003", grams: 80 }, { id: "ifct:T013", grams: 15 }, { id: "cofid:11-973", grams: 180 }],
    extraKcal: 100,
    tolerance: 0.35,
  },
  {
    id: "chicken-tikka-masala-rice",
    text: "chicken tikka masala and a plate of steamed rice",
    mealType: "dinner",
    expect: [{ label: "chicken tikka masala", accept: ["cofid:19-296", ...ANY_DISH] }, { label: "rice", accept: RICE }],
    // 200 g curry; 200 g boiled rice.
    ref: [{ id: "cofid:19-296", grams: 200 }, { id: "cofid:11-858", grams: 200 }],
    tolerance: 0.3,
  },
  {
    id: "masala-dosa",
    text: "masala dosa with sambar and coconut chutney",
    mealType: "breakfast",
    expect: [
      { label: "masala dosa", accept: ["cofid:15-513", ...ANY_DISH] },
      { label: "sambar", accept: ["cofid:12-467", ...ANY_DISH] },
      { label: "chutney", accept: [/^ifct:H00[67]$/, /^cofid:14-8(16|73)$/, ...ANY_DISH] },
    ],
    // USDA "Dosa, with filling" (184 kcal/100 g) × 200 g; 150 g sambar; 40 g coconut chutney (100 kcal).
    ref: [{ id: "fdc:2709129", grams: 200, kcalPer100g: 184 }, { id: "cofid:12-467", grams: 150 }],
    extraKcal: 100,
    tolerance: 0.35,
  },
  {
    id: "plain-dosa",
    text: "one plain dosa",
    mealType: "breakfast",
    expect: [{ label: "dosa", accept: ["inpf:648", ...ANY_DISH] }],
    // USDA "Dosa, plain" (210 kcal/100 g) × 120 g.
    ref: [{ id: "fdc:2708347", grams: 120, kcalPer100g: 210 }],
    tolerance: 0.35,
  },
  {
    id: "idli-sambar",
    text: "3 idli with sambar",
    mealType: "breakfast",
    expect: [{ label: "idli", accept: [/^inpf:6(51|73|74|81)$/, ...ANY_DISH] }, { label: "sambar", accept: ["cofid:12-467", ...ANY_DISH] }],
    // 3 idli × 40 g ≈ 39 g raw rice + 13 g urad dal; 150 g sambar.
    ref: [{ id: "ifct:A015", grams: 39 }, { id: "ifct:B003", grams: 13 }, { id: "cofid:12-467", grams: 150 }],
    tolerance: 0.35,
  },
  {
    id: "chole-bhature",
    text: "chole bhature",
    mealType: "lunch",
    expect: [
      { label: "chole", accept: [/^cofid:13-(07[4-7]|66[2-9]|670)$/, /^cofid:15-74\d$/, ...ANY_DISH] },
      { label: "bhatura", accept: ["cofid:11-911", ...ANY_DISH] },
    ],
    // 150 g boiled chickpeas + 10 g oil in the curry; 2 bhature × 60 g (puri as the closest entry).
    ref: [{ id: "cofid:13-662", grams: 150 }, { id: "ifct:T005", grams: 10 }, { id: "cofid:11-911", grams: 120 }],
    tolerance: 0.35,
  },
  {
    id: "samosas",
    text: "2 aloo samosas",
    mealType: "snack",
    expect: [{ label: "samosa", accept: ["cofid:15-305", /^cofid:19-(326|59[67])$/, /^inpf:80[12]$/, ...ANY_DISH] }],
    // 2 × 70 g vegetable samosa.
    ref: [{ id: "cofid:15-305", grams: 140 }],
    tolerance: 0.35,
  },
  {
    id: "pakoras",
    text: "6 onion pakoras",
    mealType: "snack",
    expect: [{ label: "pakora", accept: [/^cofid:15-(620|82[6-9]|830)$/, ...ANY_DISH] }],
    // 6 × 20 g.
    ref: [{ id: "cofid:15-828", grams: 120 }],
    tolerance: 0.35,
  },
  {
    id: "gulab-jamun",
    text: "2 gulab jamuns",
    mealType: "snack",
    expect: [{ label: "gulab jamun", accept: [/^cofid:11-108[13]$/, "inpf:712", ...ANY_DISH] }],
    // 2 × 40 g.
    ref: [{ id: "cofid:11-1081", grams: 80 }],
    tolerance: 0.35,
  },
  // ---- Indian packaged foods ----
  {
    id: "maggi",
    text: "one packet of Maggi 2-minute masala noodles",
    mealType: "snack",
    expect: [{ label: "Maggi", accept: [/^inpf:6(3[45]|4[23])$/, /^off:/, /^fdc:/] }],
    // 70 g pack (dry weight).
    ref: [{ id: "inpf:635", grams: 70 }],
    tolerance: 0.2,
  },
  {
    id: "good-day",
    text: "4 Britannia Good Day cashew cookies",
    mealType: "snack",
    expect: [{ label: "Good Day", accept: [/^inpf:(6|10|17|21|56|93)$/, /^off:/] }],
    // 4 × 10 g.
    ref: [{ id: "inpf:6", grams: 40 }],
    tolerance: 0.3,
  },
  {
    id: "haldiram-moong-dal",
    text: "small pack of Haldiram's moong dal namkeen, 40 g",
    mealType: "snack",
    expect: [{ label: "moong dal namkeen", accept: ["inpf:338", /^off:/] }],
    ref: [{ id: "inpf:338", grams: 40 }],
    tolerance: 0.15,
  },
  {
    id: "lays-classic",
    text: "a 52 g pack of Lay's classic salted chips",
    mealType: "snack",
    expect: [{ label: "Lay's", accept: ["inpf:272", /^off:/, /^fdc:/] }],
    ref: [{ id: "inpf:272", grams: 52 }],
    tolerance: 0.15,
  },
  {
    id: "amul-toned-milk",
    text: "a glass of Amul toned milk",
    mealType: "breakfast",
    expect: [{ label: "toned milk", accept: ["inpf:213", /^ifct:L00\d$/, /^cofid:12-3\d\d$/, /^off:/, /^fdc:/] }],
    // 250 ml.
    ref: [{ id: "inpf:213", grams: 250 }],
    tolerance: 0.25,
  },
  // ---- Global foods ----
  {
    id: "porridge",
    text: "a bowl of porridge oats made with milk",
    mealType: "breakfast",
    expect: [{ label: "porridge", accept: [/^cofid:11-7(8[89]|9\d)$/, ...ANY_DISH] }],
    // 250 g cooked.
    ref: [{ id: "cofid:11-789", grams: 250 }],
    tolerance: 0.35,
  },
  {
    id: "pizza",
    text: "2 slices of cheese and tomato pizza",
    mealType: "dinner",
    expect: [{ label: "pizza", accept: [/^cofid:11-(936|101[1-5])$/, /^fdc:/] }],
    // 2 × 80 g slices.
    ref: [{ id: "cofid:11-936", grams: 160 }],
    tolerance: 0.3,
  },
  {
    id: "chicken-breast-salad",
    text: "150 g grilled chicken breast with a green salad",
    mealType: "lunch",
    expect: [
      { label: "chicken breast", accept: [/^cofid:18-3(0[7-9]|2[3-5])$/, /^fdc:/] },
      { label: "salad", accept: [/^ifct:/, /^cofid:/, /^fdc:/, "components"] },
    ],
    // 150 g chicken; salad ≈ 20 kcal.
    ref: [{ id: "cofid:18-323", grams: 150 }],
    extraKcal: 20,
    tolerance: 0.25,
  },
  {
    id: "spaghetti-bolognese",
    text: "a plate of spaghetti bolognese",
    mealType: "dinner",
    expect: [{ label: "spaghetti bolognese", accept: ["cofid:19-483", /^cofid:1[19]-\d+$/, ...ANY_DISH] }],
    // 350 g.
    ref: [{ id: "cofid:19-483", grams: 350 }],
    tolerance: 0.35,
  },
  {
    id: "cornflakes-milk",
    text: "cornflakes with milk",
    mealType: "breakfast",
    expect: [
      { label: "cornflakes", accept: [/^cofid:11-74[2-9]$/, /^fdc:/, /^off:/] },
      { label: "milk", accept: [/^ifct:L00\d$/, /^cofid:12-3\d\d$/, /^inpf:21\d$/, /^fdc:/] },
    ],
    // 30 g cereal + 150 ml whole milk.
    ref: [{ id: "cofid:11-742", grams: 30 }, { id: "ifct:L002", grams: 150 }],
    tolerance: 0.3,
  },
  {
    id: "peanut-butter-toast",
    text: "one slice of toast with peanut butter",
    mealType: "breakfast",
    expect: [
      { label: "toast", accept: [/^cofid:11-(1001|1136|1004|982|1145|97\d)$/, /^fdc:/] },
      { label: "peanut butter", accept: ["cofid:14-892", /^inpf:76[89]$/, "inpf:770", /^fdc:/] },
    ],
    // 30 g toast + 16 g (one tablespoon) peanut butter.
    ref: [{ id: "cofid:11-1001", grams: 30 }, { id: "cofid:14-892", grams: 16 }],
    tolerance: 0.3,
  },
  {
    id: "cappuccino",
    text: "a cappuccino",
    mealType: "snack",
    expect: [{ label: "cappuccino", accept: ["cofid:17-632", /^fdc:/] }],
    // 250 ml.
    ref: [{ id: "cofid:17-632", grams: 250 }],
    tolerance: 0.35,
  },
  {
    id: "salmon-rice",
    text: "grilled salmon fillet with boiled rice",
    mealType: "dinner",
    expect: [{ label: "salmon", accept: ["ifct:P068", /^cofid:16-\d+$/, /^fdc:/] }, { label: "rice", accept: RICE }],
    // 150 g salmon; 150 g boiled rice.
    ref: [{ id: "ifct:P068", grams: 150 }, { id: "cofid:11-858", grams: 150 }],
    tolerance: 0.3,
  },
  // ---- Not food ----
  {
    id: "not-food",
    text: "my keyboard",
    mealType: "snack",
    expect: [],
    ref: [],
    expectEmpty: true,
  },
];
