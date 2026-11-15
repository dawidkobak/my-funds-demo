-- Reviewed, hand-authored fictional reports. No private report content or AI calls.
-- Only the intact V2 placeholder is replaced; portfolio/catalogue data is untouched.
LOCK TABLE mf_stock_analysis IN EXCLUSIVE MODE;

DO $analysis_preflight$
BEGIN
  IF (current_database() <> 'my-funds-demo'
      AND current_database() NOT LIKE 'my-funds-demo-test%') OR NOT EXISTS (
    SELECT 1 FROM mf_demo_dataset_identity
    WHERE mf_demo_dataset_identity.id = 1
      AND mf_demo_dataset_identity.dataset_key = 'my-funds-fictional-demo'
      AND mf_demo_dataset_identity.synthetic
      AND mf_demo_dataset_identity.as_of_date = '2026-11-13'::date
  ) OR (SELECT array_agg(mf_user.username ORDER BY mf_user.id) FROM mf_user)
       IS DISTINCT FROM ARRAY['demo']::varchar[] THEN
    RAISE EXCEPTION 'V6 requires the isolated fictional demo identity and user';
  END IF;

  -- Full V2 row fingerprint, including source, report, metadata and all nullable
  -- fields/timestamps; the generated ID is deliberately excluded.
  IF (SELECT COUNT(*) FROM mf_stock_analysis) <> 1
     OR NOT EXISTS (
       SELECT 1 FROM mf_stock_analysis
       WHERE md5((to_jsonb(mf_stock_analysis) - 'id')::text) = 'd907d55f3931998d4e4ac02459d97516'
     ) THEN
    RAISE EXCEPTION 'V6 refuses to replace edited stock analyses; preserve them and review a separate transition';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM mf_stock WHERE mf_stock.id = 9 AND mf_stock.name = 'NVIDIA') THEN
    RAISE EXCEPTION 'V6 requires the reviewed NVIDIA stock link';
  END IF;
END
$analysis_preflight$;

CREATE TEMP TABLE demo_analysis_reports (
  stock_id integer,
  company_name text NOT NULL,
  ticker text NOT NULL,
  country text NOT NULL,
  industry text NOT NULL,
  source_markdown text NOT NULL,
  report_markdown text NOT NULL,
  scores jsonb NOT NULL,
  decision text NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_analysis_reports VALUES
(NULL, 'Oracle', 'ORCL', 'USA', 'Oprogramowanie i infrastruktura chmurowa',
$oracle_source$# Oracle — fikcyjny materiał wejściowy

Scenariusz demonstracyjny z datą 2026-11-13. Wszystkie liczby, oceny i progi są modelowe, nie są wynikami Oracle ani notowaniami rynkowymi. Okresy A/B/C oznaczają dwanaście miesięcy do 30 września 2024/2025/2026 i nie odwzorowują lat obrotowych emitenta. Kwoty w mld USD, poza EPS i ceną w USD na akcję.

| Parametr modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 48,6 | 54,2 | 61,8 |
| Zysk operacyjny | 13,6 | 15,7 | 18,5 |
| Zysk netto | 8,1 | 9,2 | 10,6 |
| Przepływy operacyjne | 15,4 | 17,9 | 21,1 |
| CAPEX | 7,2 | 12,8 | 19,6 |
| Wolne przepływy (operacyjne − CAPEX) | 8,2 | 5,1 | 1,5 |

Dodatkowe założenia C: dług netto 69,0; EBITDA 23,0; 2,8 mld akcji, brak zmiany ich liczby w scenariuszu; modelowa cena odniesienia 151,20 USD na 2026-11-13. Wzrost ma pochodzić z baz danych, aplikacji abonamentowych i infrastruktury chmurowej. Brak danych o rentowności pojedynczych kontraktów, harmonogramie refinansowania i podziale CAPEX-u na utrzymanie oraz rozwój.

Publiczny kontekst biznesowy: https://www.oracle.com/database/ i https://www.oracle.com/cloud/. Linki opisują produkty, nie potwierdzają modelowych liczb. Materiał napisany od zera do demo; wykorzystano wyłącznie ogólny układ sekcji wcześniejszych analiz.$oracle_source$,
$oracle_report$# Oracle — chmura rośnie, gotówka pod presją

> **Fikcyjna analiza demonstracyjna · 2026-11-13.** Wszystkie liczby, punktacja, cena i warunki decyzji są modelowe. Nie są rzeczywistymi wynikami Oracle, prognozą ani rekomendacją inwestycyjną. Publiczne linki służą wyłącznie opisowi biznesu. Horyzont tezy: trzy lata; rdzeń portfela: szeroki globalny ETF.

## 1. Identyfikacja
Oracle · ORCL · USA · oprogramowanie i infrastruktura chmurowa. Analiza nie jest powiązana z pozycją w portfelu demo. Modelowe okresy A/B/C kończą się 30 września 2024/2025/2026; nie odpowiadają raportowanym latom obrotowym spółki. Wszystkie kwoty w mld USD, chyba że wskazano inaczej.

## 2. Opis biznesu
Oracle sprzedaje bazy danych, aplikacje dla przedsiębiorstw oraz usługi chmurowe. Wdrożenie systemu krytycznego dla księgowości czy operacji klienta może tworzyć koszt zmiany dostawcy. To inny mechanizm niż sama popularność infrastruktury AI: kluczowe są odnowienia umów, jakość usług oraz koszt utrzymania klienta.

W modelu tradycyjny biznes finansuje rozwój infrastruktury. Przychód abonamentowy nie oznacza jednak automatycznie wolnej gotówki: przed obsługą większego obciążenia trzeba sfinansować centra danych. Dlatego nie utożsamiamy wzrostu sprzedaży z poprawą atrakcyjności akcji.

## 3. Dlaczego spółka może być lepsza od indeksu
**Teza:** istniejące relacje z przedsiębiorstwami mogą ułatwić sprzedaż usług chmurowych, a po okresie rozbudowy infrastruktury poprawić konwersję zysku na gotówkę. Przewaga nad ETF wymaga zarówno wzrostu, jak i zdyscyplinowanego CAPEX-u; samo większe zapotrzebowanie na moc obliczeniową nie wystarcza.

## 4. Twarde bramki
| Bramka | Ocena w modelu | Uzasadnienie |
| --- | --- | --- |
| Wzrost przychodów | Przechodzi | 48,6 → 54,2 → 61,8 |
| Rentowność | Przechodzi | Dodatni i rosnący zysk operacyjny |
| Zadłużenie | Przechodzi warunkowo | Dług netto / EBITDA = 69,0 / 23,0 = 3,0×; poziom graniczny, nie komfortowy |
| Zrozumiały, powtarzalny biznes | Przechodzi | Bazy danych, aplikacje i umowy chmurowe |
| Nie tylko moda lub jeden kontrakt | Przechodzi | Założono istniejącą bazę klientów poza infrastrukturą AI |
| Teza względem indeksu | Przechodzi | Możliwa poprawa konwersji gotówki po inwestycjach |

Bramki przechodzą **wyłącznie w scenariuszu**. Bez harmonogramu długu nie można przenieść tej oceny na rzeczywistą spółkę. Warunkowa bramka nie jest zgodą na dalszy wzrost dźwigni.

## 5. Przychody
| Okres modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 48,6 | 54,2 | 61,8 |
| Zysk operacyjny | 13,6 | 15,7 | 18,5 |
| Marża operacyjna | 28,0% | 29,0% | 29,9% |

Wzrost sprzedaży wynosi około 11,5% w B i 14,0% w C. To model stopniowego przyspieszenia, nie wielokrotnego wzrostu w jednym roku. Należy oddzielić zużycie usług chmurowych od podpisanej wartości umów: umowa wieloletnia nie staje się w całości przychodem ani gotówką w dniu podpisania.

## 6. Zyski, marże, bilans i przepływy
Zysk netto rośnie z 8,1 do 10,6, lecz wolne przepływy spadają z 8,2 przez 5,1 do 1,5. W C przepływy operacyjne 21,1 minus CAPEX 19,6 dają zaledwie 1,5, czyli 14,2% zysku netto. To główna rozbieżność tego raportu: rachunek wyników wygląda lepiej niż zdolność do redukcji długu.

Dług netto / EBITDA 3,0× wymaga obserwacji. EBITDA pomija nakłady inwestycyjne i nie jest środkami dostępnymi na spłatę zadłużenia. Brak danych o zapadalności długu i kosztach odsetkowych blokuje bardziej stanowczą ocenę bilansu.

## 7. Fosa i przewagi
Koszt migracji danych, integracje z procesami klienta i kompetencje administratorów mogą utrudniać zmianę dostawcy. Te przewagi trzeba oceniać osobno od chmury: silna pozycja w bazach danych nie dowodzi automatycznie przewagi kosztowej w każdej kategorii infrastruktury.

W modelu fosa otrzymuje 3/3 za trwałość istniejących wdrożeń, nie za założenie, że konkurent nie może odebrać nowych projektów. Pogorszenie odnowień byłoby ważniejsze niż medialny rozmiar pojedynczej umowy.

## 8. Konkurencja i branża
Konkurencja obejmuje inne platformy chmurowe, systemy bazodanowe i aplikacje biznesowe. Klient może używać kilku dostawców równocześnie. Branża jest zrozumiała, ale wymaga kapitału i ciągłego rozwoju produktu; konkurencję oceniamy na 1/3, a branżę na 2/3.

## 9. Zarząd i alokacja kapitału
Priorytetem powinno być finansowanie projektów z mierzalnym zwrotem, utrzymanie jakości usług i ograniczenie ryzyka refinansowania. Skup akcji finansowany długiem przy spadającym FCF nie byłby automatycznie korzystny dla akcjonariusza. Modelowa ocena 2/3 zakłada ostrożne tempo inwestycji po okresie C; nie jest oceną rzeczywistych działań zarządu.

## 10. Wycena
Modelowa cena 151,20 USD × 2,8 mld akcji daje kapitalizację 423,36 mld USD. EPS = 10,6 / 2,8 = 3,79 USD, więc P/E ≈ 39,9×. Modelowa rentowność FCF to tylko 1,5 / 423,36 ≈ 0,35%. Wszystkie wartości dotyczą tej samej fikcyjnej daty; nie używamy ich do wyceny portfela aplikacji.

Cena wymaga odbudowy gotówki. W wariancie bez poprawy FCF wysoki mnożnik zysku nie znajduje wystarczającego oparcia w przepływach. Nie podajemy ceny docelowej: bez podziału CAPEX-u i harmonogramu długu byłaby pozornie precyzyjna.

## 11. Ryzyka i czerwone flagi
- CAPEX rośnie szybciej niż sprzedaż i pochłania gotówkę potrzebną do obsługi długu.
- Duże umowy mogą zwiększać koncentrację klientów i ryzyko wykonania.
- Opóźnienia infrastruktury mogą przesunąć przychody, lecz nie wszystkie wydatki.
- Klienci mogą negocjować ceny lub dywersyfikować dostawców.

## 12. Scoring
| Kryterium | Punkty 0–3 | Powód |
| --- | ---: | --- |
| Przychody | 3 | Stabilny wzrost modelowy |
| Zyski | 3 | Dodatni wzrost zysku netto |
| Marże | 2 | Poprawa, lecz słaba konwersja na FCF |
| Fosa | 3 | Koszty zmiany istniejących systemów |
| Konkurencja | 1 | Silni dostawcy infrastruktury i aplikacji |
| Branża | 2 | Powtarzalna, ale kapitałochłonna |
| Zarząd i alokacja kapitału | 2 | Warunkowo zdyscyplinowane inwestycje |
| Wycena | 0 | Wymagający P/E i bardzo niska rentowność FCF |
| **Razem** | **16/24** | **Obserwować** |

## 13. Najważniejsze wnioski
W modelu to dobry przykład różnicy między jakością produktu a atrakcyjnością inwestycji. Sprzedaż i zysk rosną, lecz CAPEX oraz dźwignia ograniczają margines bezpieczeństwa. Najważniejsza liczba do kolejnego przeglądu to FCF po inwestycjach, nie sam wzrost przychodów.

## 14. Decyzja
**OBSERWOWAC.** Trzyletnia teza wymaga wykorzystania nowej infrastruktury i odbudowy wolnej gotówki bez zwiększenia dźwigni. Modelowy warunek przeglądu: dwa kolejne okresy ze wzrostem FCF oraz spadkiem dług netto / EBITDA poniżej 2,5×. Warunek odrzucenia tezy: dźwignia ponad 3,5× lub utrzymujący się ujemny FCF bez wiarygodnego planu finansowania. To kryteria ćwiczenia, nie sygnały transakcyjne.

## 15. Braki danych i publiczny kontekst
Brak segmentowej rentowności, terminów długu, odsetek, retencji klientów i rozdzielenia CAPEX-u. Przed rzeczywistą analizą potrzebne są raporty emitenta oraz bieżące dane o cenie i liczbie akcji.

[Produkty bazodanowe Oracle](https://www.oracle.com/database/) · [Oracle Cloud](https://www.oracle.com/cloud/). Źródła opisują biznes; wszystkie liczby powyżej zostały wymyślone do demo. Nie wykorzystano prywatnych tez, wyników ani materiałów wejściowych.$oracle_report$,
'{"przychody":3,"zyski":3,"marze":2,"fosa":3,"konkurencja":1,"branza":2,"zarzadIAlokacjaKapitalu":2,"wycena":0}', 'OBSERWOWAC'),
(NULL, 'MercadoLibre', 'MELI', 'Urugwaj / Ameryka Łacińska', 'E-commerce i płatności',
$meli_source$# MercadoLibre — fikcyjny materiał wejściowy

Scenariusz na 2026-11-13, nie raport finansowy emitenta. Okresy A/B/C: dwanaście miesięcy do 30 września 2024/2025/2026. Wszystkie kwoty w mld USD, poza ceną i EPS w USD na akcję; dane nie odpowiadają latom obrotowym ani rzeczywistym wynikom.

| Parametr modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 17,3 | 21,4 | 26,8 |
| Zysk operacyjny | 1,90 | 2,57 | 3,48 |
| Zysk netto | 1,15 | 1,53 | 2,07 |
| Przepływy operacyjne po korekcie środków klientów | 2,40 | 3,15 | 4,10 |
| CAPEX | 0,90 | 1,10 | 1,35 |
| Wolne przepływy | 1,50 | 2,05 | 2,75 |

Założenia C: 0,050 mld akcji bez zmiany ich liczby; cena modelowa 1 740 USD; dług korporacyjny netto / EBITDA 0,8×. Środki klientów są wydzielone i nie stanowią gotówki dla akcjonariuszy. Portfel kredytowy, koszt ryzyka i jego finansowanie wymagają osobnej oceny; brak danych pozwalających zastosować przemysłową miarę długu do całego fintechu.

Publiczny kontekst: https://investor.mercadolibre.com/. Fikcyjne tezy, liczby i progi napisano od zera; z wcześniejszych raportów wykorzystano tylko ogólny układ sekcji.$meli_source$,
$meli_report$# MercadoLibre — siła ekosystemu, ryzyko kredytu

> **Fikcyjna analiza demonstracyjna · 2026-11-13.** Liczby, cena, punktacja i decyzja są modelowe, nie są rzeczywistymi wynikami MercadoLibre ani rekomendacją. Opis biznesu korzysta jedynie z publicznego kontekstu. Horyzont trzyletni; pojedyncza spółka jest satelitą szerokiego ETF, a nie jego zamiennikiem.

## 1. Identyfikacja
MercadoLibre · MELI · Ameryka Łacińska · handel internetowy i usługi płatnicze. Brak powiązania z aktywem w portfelu demo. A/B/C to modelowe okresy dwunastomiesięczne do 30 września 2024/2025/2026, nie oficjalne lata obrotowe. Kwoty w mld USD, poza ceną i EPS na akcję.

## 2. Opis biznesu
Platforma łączy kupujących ze sprzedawcami, a usługi płatnicze i logistyczne wspierają dokonanie transakcji. Większa liczba zamówień może poprawiać wykorzystanie sieci dostaw. Płatności działają także poza platformą handlową, więc oba obszary nie są identycznymi strumieniami przychodu.

Kredyt może zwiększać aktywność klientów, ale zmienia profil ryzyka: wzrost portfela pożyczek nie jest sam w sobie dowodem jakości biznesu. Model traktuje środki powierzone przez klientów osobno od gotówki należącej do spółki.

## 3. Dlaczego spółka może być lepsza od indeksu
**Teza:** wspólny ekosystem handlu, dostaw i płatności może zwiększać częstotliwość zakupów oraz efektywność obsługi. Przewaga nad indeksem wymaga wzrostu zysku na akcję po koszcie ryzyka kredytowego i walutowego, nie tylko rosnącej wartości transakcji.

## 4. Twarde bramki
| Bramka | Ocena w modelu | Uzasadnienie |
| --- | --- | --- |
| Wzrost przychodów | Przechodzi | 17,3 → 21,4 → 26,8 |
| Rentowność | Przechodzi | Zysk operacyjny i netto dodatni |
| Zadłużenie | Przechodzi warunkowo | Korporacyjny dług netto / EBITDA 0,8×; środki klientów wyłączone |
| Zrozumiały, powtarzalny biznes | Przechodzi | Transakcje, logistyka i płatności |
| Nie tylko moda lub jeden kontrakt | Przechodzi | Wielu kupujących i sprzedawców |
| Teza względem indeksu | Przechodzi | Możliwa korzyść skali wspólnego ekosystemu |

To bramki **fikcyjnego modelu**. Dla rzeczywistej analizy nie wystarczy miara długu korporacyjnego: fintech wymaga osobnego sprawdzenia płynności, finansowania kredytu i ochrony pieniędzy klientów.

## 5. Przychody
| Okres modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 17,3 | 21,4 | 26,8 |
| Zysk operacyjny | 1,90 | 2,57 | 3,48 |
| Marża operacyjna | 11,0% | 12,0% | 13,0% |

Modelowy wzrost sprzedaży wynosi około 23,7% i 25,2%. Marża poprawia się stopniowo, a nie skokowo. Wartość zakupów i wolumen płatności nie są przychodem spółki: nie sumujemy ich z raportowaną sprzedażą ani ze sobą jako miary dochodu.

## 6. Zyski, marże, bilans i przepływy
Zysk netto rośnie z 1,15 do 2,07. Skorygowane przepływy operacyjne C wynoszą 4,10; po CAPEX 1,35 pozostaje 2,75. FCF / zysk netto ≈ 132,9%, lecz to **założenie modelowe po wyłączeniu środków klientów**, a nie surowy przepływ z działalności finansowej.

Nie traktujemy sald portfeli płatniczych jak wolnej gotówki na skup akcji. Brak danych o należnościach przeterminowanych, rezerwach i zapadalności finansowania kredytu jest istotnym ograniczeniem. Dobra konwersja gotówki w handlu nie neutralizuje ryzyka pożyczek.

## 7. Fosa i przewagi
Sieć sprzedawców, rozpoznawalność platformy, obsługa płatności oraz logistyka mogą wzajemnie się wzmacniać. Dane o historii transakcji mogą wspierać ocenę ryzyka, lecz nie eliminują strat kredytowych podczas osłabienia gospodarki.

Fosa otrzymuje modelowe 3/3, ale nie jest jednakowa w każdym kraju. Utrata jakości dostaw albo zaufania do usług płatniczych mogłaby osłabić kilka części ekosystemu jednocześnie.

## 8. Konkurencja i branża
Konkurencja obejmuje platformy handlowe, lokalnych detalistów, banki i operatorów płatności. Wymusza inwestycje w logistykę oraz promocje. Modelowe 1/3 za konkurencję i 2/3 za branżę odzwierciedlają atrakcyjną usługę przy dużej złożoności regionalnej.

## 9. Zarząd i alokacja kapitału
Model preferuje inwestycje w wydajność dostaw i utrzymanie klientów ponad wzrost kredytu za wszelką cenę. Ocena 2/3 zakłada mierzenie rentowności po stratach kredytowych, a nie wyłącznie wzrostu wolumenu. Brak danych o rozwodnieniu oznacza, że tezę należy sprawdzić na akcję, nie tylko na poziomie całej firmy.

## 10. Wycena
Modelowa cena 1 740 USD i 0,050 mld akcji dają kapitalizację 87,0 mld USD. EPS C = 2,07 / 0,050 = 41,40 USD, a P/E ≈ 42,0×. FCF yield = 2,75 / 87,0 ≈ 3,16%. Nie porównujemy tej ceny z portfelem aplikacji: to osobny punkt ćwiczenia na 2026-11-13.

Wycena jest wymagająca i zostawia ograniczony margines na straty kredytowe lub zmianę FX. Wariant trzyletni zakłada wzrost zysku na akcję rzędu 15–20% rocznie, nie wielokrotny wzrost kursu. Jeśli wzrost spadnie do jednocyfrowego, wysoki mnożnik może istotnie obniżyć atrakcyjność inwestycji.

## 11. Ryzyka i czerwone flagi
- Dewaluacja walut może zmniejszyć wynik w USD mimo wzrostu lokalnego biznesu.
- Szybki wzrost kredytu może ukryć pogorszenie jakości należności.
- Koszty logistyki i promocji mogą wzrosnąć przed przychodami.
- Regulacje dotyczące płatności i ochrony klienta różnią się między krajami.
- Środki klientów nie są gotówką dostępną akcjonariuszom.

## 12. Scoring
| Kryterium | Punkty 0–3 | Powód |
| --- | ---: | --- |
| Przychody | 3 | Silny i powtarzalny wzrost w modelu |
| Zyski | 3 | Zysk netto rośnie wraz ze skalą |
| Marże | 3 | Stopniowa poprawa rentowności |
| Fosa | 3 | Handel, płatności i logistyka wzmacniają się |
| Konkurencja | 1 | Silni gracze w kilku kategoriach |
| Branża | 2 | Zrozumiała potrzeba, regionalna złożoność |
| Zarząd i alokacja kapitału | 2 | Warunkowa dyscyplina kredytowa |
| Wycena | 1 | Wymagająca mimo jakości biznesu |
| **Razem** | **18/24** | **Głębsza analiza** |

## 13. Najważniejsze wnioski
Model pokazuje biznes z efektem skali, ale połączenie handlu i finansowania wymaga więcej ostrożności niż prosty marketplace. Najważniejsze pytanie brzmi: czy zysk i gotówka rosną po uwzględnieniu kosztu kredytu oraz zmian walut?

## 14. Decyzja
**GLEBSZA_ANALIZA.** Teza trzyletnia opiera się na wzroście częstotliwości transakcji i utrzymaniu jakości kredytu. Modelowy warunek dalszej pracy: rozdzielenie gotówki spółki od środków klientów, analiza kohort pożyczek i wyniku w stałych walutach. Warunek przeglądu lub wyjścia z tezy: dwa okresy pogorszenia marży poniżej 10% wraz ze wzrostem strat kredytowych lub problemem płynności. To plan analityczny, nie polecenie kupna.

## 15. Braki danych i publiczny kontekst
Brak danych o kosztach ryzyka, rezerwach, strukturze finansowania, marżach krajowych i rozwodnieniu. W modelu nie przeliczamy USD na PLN ani nie sumujemy wyników w różnych walutach bez kursu.

[Relacje inwestorskie MercadoLibre](https://investor.mercadolibre.com/). Strona jest źródłem publicznego kontekstu, nie modelowych liczb. Raport napisano od zera bez prywatnych materiałów.$meli_report$,
'{"przychody":3,"zyski":3,"marze":3,"fosa":3,"konkurencja":1,"branza":2,"zarzadIAlokacjaKapitalu":2,"wycena":1}', 'GLEBSZA_ANALIZA'),
(9, 'NVIDIA', 'NVDA', 'USA', 'Półprzewodniki i obliczenia przyspieszone',
$nvda_source$# NVIDIA — fikcyjny materiał wejściowy

Scenariusz demonstracyjny na 2026-11-13. Wszystkie liczby i warunki są wymyślone. A/B/C to okresy dwunastomiesięczne do 30 września 2024/2025/2026, nie rzeczywiste lata obrotowe NVIDIA. Kwoty w mld USD, poza ceną i EPS w USD na akcję.

| Parametr modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 83,4 | 112,8 | 146,2 |
| Zysk operacyjny | 43,4 | 59,8 | 74,6 |
| Zysk netto | 37,5 | 51,2 | 64,3 |
| Przepływy operacyjne | 40,8 | 55,4 | 69,1 |
| CAPEX | 3,2 | 4,1 | 5,4 |
| Wolne przepływy | 37,6 | 51,3 | 63,7 |

Założenia C: gotówka netto 28,4; 24,5 mld akcji bez zmiany ich liczby; cena modelowa 137,20 USD na 2026-11-13. Model zakłada ekosystem sprzętu i oprogramowania oraz ryzyko koncentracji odbiorców, ograniczeń eksportu i zależności od produkcji zewnętrznej. Brak danych o zapasach, udziale największych klientów i warunkach zamówień.

Publiczny kontekst: https://www.nvidia.com/en-us/data-center/ i https://developer.nvidia.com/cuda-zone. Cena z raportu nie zastępuje notowań portfela demo. Treść napisana od zera.$nvda_source$,
$nvda_report$# NVIDIA — przewaga ekosystemu kontra oczekiwania

> **Fikcyjna analiza demonstracyjna · 2026-11-13.** Wszystkie liczby, cena, oceny i progi są modelowe, nie są wynikami NVIDIA ani rekomendacją inwestycyjną. Raport nie korzysta z przyszłych raportów emitenta ani z prywatnych analiz. Horyzont: trzy lata; szeroki ETF pozostaje rdzeniem portfela.

## 1. Identyfikacja
NVIDIA · NVDA · USA · półprzewodniki i obliczenia przyspieszone. Powiązanie z istniejącym aktywem NVIDIA w aplikacji służy wyłącznie nawigacji. Cena odniesienia raportu nie jest ceną wyceny tego aktywa. Okresy A/B/C kończą się modelowo 30 września 2024/2025/2026 i nie odpowiadają rzeczywistym latom obrotowym. Kwoty w mld USD, poza ceną i EPS.

## 2. Opis biznesu
NVIDIA dostarcza układy i platformy wykorzystywane w obliczeniach przyspieszonych. Oprogramowanie, biblioteki i narzędzia programistyczne wpływają na użyteczność sprzętu. Klient kupuje możliwość wykonania zadania i koszt całego rozwiązania, nie tylko liczbę procesorów.

Projektowanie i ekosystem nie są jednak tym samym co posiadanie całego łańcucha dostaw. Dostępność produkcji, pamięci i zaawansowanego pakowania może ograniczać realizację zamówień. W modelu przewaga wynika ze współdziałania sprzętu oraz oprogramowania, nie z twierdzenia, że konkurencja nie istnieje.

## 3. Dlaczego spółka może być lepsza od indeksu
**Teza:** trudny do odtworzenia ekosystem może utrzymać udział w wydatkach na obliczenia przyspieszone oraz wysoką konwersję gotówki. Aby akcja pobiła ETF, wyniki muszą uzasadnić cenę nawet przy spowolnieniu tempa wzrostu. Nie zakładamy wzrostu kursu 10× ani stałej przewagi każdej generacji produktu.

## 4. Twarde bramki
| Bramka | Ocena w modelu | Uzasadnienie |
| --- | --- | --- |
| Wzrost przychodów | Przechodzi | 83,4 → 112,8 → 146,2 |
| Rentowność | Przechodzi | Dodatni zysk i wysoka marża |
| Zadłużenie | Przechodzi | Założono 28,4 gotówki netto |
| Zrozumiały, powtarzalny biznes | Przechodzi | Sprzęt i platforma programistyczna |
| Nie tylko moda lub jeden kontrakt | Przechodzi | Założono wielu odbiorców i kilka zastosowań obliczeń |
| Teza względem indeksu | Przechodzi | Trwałość ekosystemu oraz przepływy, nie sama moda |

Bramki przechodzą w scenariuszu. Rzeczywista ocena wymagałaby danych o koncentracji klientów, zapasach i rozkładzie zastosowań; popularność AI nie zastępuje tych danych.

## 5. Przychody
| Okres modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 83,4 | 112,8 | 146,2 |
| Zysk operacyjny | 43,4 | 59,8 | 74,6 |
| Marża operacyjna | 52,0% | 53,0% | 51,0% |

Wzrost sprzedaży wynosi około 35,3% w B i 29,6% w C: tempo maleje, mimo że przyrost kwotowy nadal jest duży. Model nie wymaga wiecznego przyspieszania. Spadek marży C pokazuje, że nowe produkty, konkurencja i koszty dostaw mogą ograniczyć część korzyści ze skali.

## 6. Zyski, marże, bilans i przepływy
Zysk netto C wynosi 64,3, a FCF = 69,1 − 5,4 = 63,7. Konwersja FCF / zysk netto to około 99,1%. Gotówka netto 28,4 zmniejsza ryzyko refinansowania, ale nie chroni przed nadmierną wyceną lub spadkiem zamówień.

Niski własny CAPEX nie oznacza braku zobowiązań ekonomicznych. Zaliczki, rezerwacje zdolności produkcyjnych i zapasy wymagają analizy. Nie porównujemy modelowej marży bezpośrednio z marżą firm posiadających własne fabryki bez uwzględnienia różnic biznesowych.

## 7. Fosa i przewagi
Narzędzia CUDA, biblioteki, doświadczenie programistów i integracja rozwiązania mogą tworzyć koszt przejścia na inną platformę. Wydajność pojedynczego układu to tylko część przewagi; znaczenie mają także czas wdrożenia i dostępność specjalistów.

Modelowe 3/3 za fosę nie oznacza, że klient nie opracuje własnego akceleratora. Największą czerwoną flagą byłaby utrata przewagi w całkowitym koszcie zadania, nie chwilowa różnica w jednym benchmarku.

## 8. Konkurencja i branża
Konkurenci obejmują projektantów układów oraz wewnętrzne zespoły największych klientów. Wysoka rentowność zachęca do szukania zamienników. Branża jest cykliczna i podatna na ograniczenia dostaw; przyznajemy 1/3 za branżę oraz 1/3 za konkurencję, mimo jakości platformy.

## 9. Zarząd i alokacja kapitału
Modelowa ocena 2/3 zakłada utrzymanie inwestycji w badania i rozwój, ostrożność wobec nadmiernych zobowiązań zakupowych oraz kontrolę rozwodnienia. Skup akcji nie powinien maskować kosztu wynagrodzeń akcjami. Bez danych o liczbie akcji w czasie nie można zakładać, że wzrost zysku całej spółki przełoży się jeden do jednego na EPS.

## 10. Wycena
Modelowa cena 137,20 USD × 24,5 mld akcji daje kapitalizację 3 361,4 mld USD. EPS C = 64,3 / 24,5 ≈ 2,62 USD, a P/E ≈ 52,3×. Modelowa rentowność FCF = 63,7 / 3 361,4 ≈ 1,90%. To wymagająca cena nawet dla wysokiej jakości biznesu.

Scenariusz wrażliwości: przy niezmienionej liczbie akcji wzrost EPS o 15% rocznie przez trzy lata daje mnożnik EPS 1,52×. Jeśli P/E spadnie z 52,3× do 35×, iloczyn tych zmian to około 1,02×, czyli niemal brak zmiany ceny przed dywidendami. To ilustracja matematyczna, **nie prognoza kursu**; wynik nie zakłada zmiany FX ani podatków.

## 11. Ryzyka i czerwone flagi
- Koncentracja wydatków u dużych odbiorców oraz własne układy klientów.
- Ograniczenia eksportowe i dostęp do kluczowych elementów produkcji.
- Nadmierne zamówienia klientów lub zapasy przed zmianą generacji sprzętu.
- Presja na marże i wysokie oczekiwania zawarte w cenie.
- Dobra spółka może być słabą inwestycją przy niewłaściwym mnożniku.

## 12. Scoring
| Kryterium | Punkty 0–3 | Powód |
| --- | ---: | --- |
| Przychody | 3 | Silny wzrost przy malejącym tempie |
| Zyski | 3 | Rosnący zysk i wysoka konwersja |
| Marże | 2 | Wysokie, lecz spadek w okresie C |
| Fosa | 3 | Wspólny ekosystem sprzętu i oprogramowania |
| Konkurencja | 1 | Alternatywy i własne projekty klientów |
| Branża | 1 | Cykliczność oraz ograniczenia dostaw |
| Zarząd i alokacja kapitału | 2 | Warunkowa dyscyplina inwestycji |
| Wycena | 0 | Wysokie oczekiwania i niska rentowność FCF |
| **Razem** | **15/24** | **Obserwować** |

## 13. Najważniejsze wnioski
Jakość platformy i bilansu nie zwalnia z oceny ceny. Model celowo zestawia wysoką rentowność z ryzykiem kompresji mnożnika. W kolejnej analizie potrzebna jest odpowiedź, jaka część zamówień oznacza trwałe wykorzystanie infrastruktury, a jaka cykliczną rozbudowę.

## 14. Decyzja
**OBSERWOWAC.** Trzyletnia teza: utrzymanie ekosystemu i FCF przy stopniowo wolniejszym wzroście. Modelowy warunek ponownego przeglądu: P/E poniżej 35× przy utrzymaniu konwersji FCF powyżej 90% oraz potwierdzonej dywersyfikacji klientów. Warunek odrzucenia tezy: trwała utrata przewagi kosztowej platformy lub dwa okresy spadku sprzedaży i marży. Progi są wymyślone do ćwiczenia, nie sygnałem transakcyjnym.

## 15. Braki danych i publiczny kontekst
Brak danych o koncentracji, zapasach, zobowiązaniach wobec dostawców, regionalnej sprzedaży i rozwodnieniu. Nie używamy fikcyjnych liczb do aktualizacji notowań ani wyników portfela.

[NVIDIA Data Center](https://www.nvidia.com/en-us/data-center/) · [CUDA](https://developer.nvidia.com/cuda-zone). Linki potwierdzają publiczny kontekst produktu, nie modelowe wyniki. Raport napisano od zera.$nvda_report$,
'{"przychody":3,"zyski":3,"marze":2,"fosa":3,"konkurencja":1,"branza":1,"zarzadIAlokacjaKapitalu":2,"wycena":0}', 'OBSERWOWAC'),
(NULL, 'Apple', 'AAPL', 'USA', 'Elektronika konsumencka i usługi',
$apple_source$# Apple — fikcyjny materiał wejściowy

Scenariusz na 2026-11-13. Wszystkie liczby, oceny i progi są modelowe, nie są danymi emitenta. A/B/C oznaczają dwanaście miesięcy do 30 września 2024/2025/2026, bez odwzorowania faktycznych lat obrotowych. Kwoty w mld USD, poza ceną i EPS na akcję.

| Parametr modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 372,4 | 389,1 | 411,6 |
| Zysk operacyjny | 111,7 | 120,6 | 127,6 |
| Zysk netto | 90,2 | 97,3 | 104,8 |
| Przepływy operacyjne | 103,4 | 112,8 | 121,2 |
| CAPEX | 11,1 | 12,2 | 13,4 |
| Wolne przepływy | 92,3 | 100,6 | 107,8 |

Założenia C: gotówka netto 14,6; 14,6 mld akcji, brak zmiany ich liczby w scenariuszu wyceny; cena modelowa 218,30 USD. Wzrost napędzają odnowienia urządzeń i usługi. Brak danych o retencji, marżach geograficznych, regulacjach oraz faktycznej skali rozwodnienia i skupu akcji.

Publiczny kontekst: https://www.apple.com/services/ i https://investor.apple.com/. Dane liczbowe napisano od zera; nie wykorzystano prywatnego materiału.$apple_source$,
$apple_report$# Apple — jakość ekosystemu, ograniczony margines ceny

> **Fikcyjna analiza demonstracyjna · 2026-11-13.** Wszystkie liczby, cena, punktacja i warunki są modelowe, nie są rzeczywistymi wynikami Apple ani rekomendacją inwestycyjną. Publiczne linki opisują biznes. Trzyletnia teza nie wymaga wzrostu 10×; punktem odniesienia jest szeroki ETF.

## 1. Identyfikacja
Apple · AAPL · USA · elektronika konsumencka i usługi. Brak powiązania z aktywem w portfelu demo. Modelowe okresy A/B/C kończą się 30 września 2024/2025/2026, nie odtwarzają raportowanych lat obrotowych. Kwoty w mld USD, poza ceną i EPS.

## 2. Opis biznesu
Apple sprzedaje urządzenia i usługi zintegrowane z własnym ekosystemem. Wygoda korzystania z kilku produktów, konta i zakupionych usług może zwiększać retencję. Przychód ze sprzętu pozostaje jednak zależny od cyklu wymiany urządzeń; nie jest abonamentem tylko dlatego, że klient jest lojalny.

Usługi mogą poprawiać powtarzalność przepływów, lecz nie są jedną jednorodną kategorią. Regulacje platform, sposób dystrybucji aplikacji i warunki rozliczeń mogą wpływać na rentowność. Model nie zakłada, że każda nowa funkcja produktu automatycznie rozpocznie silniejszy cykl zakupowy.

## 3. Dlaczego spółka może być lepsza od indeksu
**Teza:** trwała retencja ekosystemu i wzrost usług mogą pozwolić zwiększać gotówkę na akcję mimo umiarkowanego wzrostu urządzeń. Przewaga nad ETF wymaga rozsądnej ceny wejścia i braku istotnego pogorszenia ekonomiki usług. Sama rozpoznawalność marki nie uzasadnia dowolnego mnożnika.

## 4. Twarde bramki
| Bramka | Ocena w modelu | Uzasadnienie |
| --- | --- | --- |
| Wzrost przychodów | Przechodzi | 372,4 → 389,1 → 411,6 |
| Rentowność | Przechodzi | Zysk netto i FCF dodatnie |
| Zadłużenie | Przechodzi | Założono 14,6 gotówki netto |
| Zrozumiały, powtarzalny biznes | Przechodzi | Urządzenia, odnowienia i usługi |
| Nie tylko moda lub jeden kontrakt | Przechodzi | Teza oparta na istniejącym ekosystemie |
| Teza względem indeksu | Przechodzi | Wzrost gotówki na akcję oraz retencja |

Bramki dotyczą tylko scenariusza. Bez rzeczywistych danych o retencji i ekonomice usług nie można uznać modelowego wyniku za ocenę obecnej sytuacji emitenta.

## 5. Przychody
| Okres modelowy | A | B | C |
| --- | ---: | ---: | ---: |
| Przychody | 372,4 | 389,1 | 411,6 |
| Zysk operacyjny | 111,7 | 120,6 | 127,6 |
| Marża operacyjna | 30,0% | 31,0% | 31,0% |

Sprzedaż rośnie modelowo o 4,5% i 5,8%, znacznie wolniej niż w raportach MercadoLibre lub NVIDIA. Dlatego jakość i konwersja gotówki są ważniejsze niż narracja szybkiego wzrostu. Nie dopisujemy przychodów przyszłych produktów, których ekonomiki nie opisano w materiale wejściowym.

## 6. Zyski, marże, bilans i przepływy
Zysk netto rośnie z 90,2 do 104,8. W C przepływy operacyjne 121,2 minus CAPEX 13,4 dają FCF 107,8, czyli około 102,9% zysku netto. Gotówka netto 14,6 zapewnia w modelu elastyczność, ale nie jest powodem, by ignorować ryzyko łańcucha dostaw i regulacji.

Liczba akcji w obliczeniu wyceny pozostaje 14,6 mld. Nie zakładamy automatycznego wzrostu EPS wskutek skupu: najpierw należy porównać skup, wynagrodzenia akcjami i cenę odkupów. Wzrost zysku na akcję oraz całkowitego zysku to różne wskaźniki.

## 7. Fosa i przewagi
Marka, integracja urządzeń, dystrybucja i koszt zmiany ekosystemu mogą utrwalać relacje z klientami. Model daje 3/3 za fosę, lecz retencja nie oznacza braku presji konkurencyjnej. Znaczenie ma to, czy klient pozostaje z powodu jakości produktu, czy tylko chwilowej bariery migracji.

## 8. Konkurencja i branża
Konkurencja obejmuje inne urządzenia, systemy operacyjne i usługi cyfrowe. Rynek dojrzały daje powtarzalne potrzeby, ale ogranicza tempo wzrostu. Modelowo konkurencja otrzymuje 1/3, a branża 2/3; ani dojrzałość, ani marka nie eliminują cyklu konsumenckiego.

## 9. Zarząd i alokacja kapitału
Ocena modelowa 2/3 zakłada zachowanie nakładów na rozwój i selektywny skup akcji. Odkup przy wysokiej cenie może być mniej efektywny niż przy tańszej wycenie, nawet gdy nominalnie zmniejsza liczbę akcji. Brak pełnych danych o bodźcach zarządu pozostaje luką, nie automatycznym pozytywem.

## 10. Wycena
Cena modelowa 218,30 USD × 14,6 mld akcji daje kapitalizację 3 187,18 mld USD. EPS C = 104,8 / 14,6 ≈ 7,18 USD, a P/E ≈ 30,4×. FCF yield = 107,8 / 3 187,18 ≈ 3,38%. Przy jednocyfrowym wzroście sprzedaży jest to cena wymagająca dalszej poprawy wyniku na akcję.

Wariant wrażliwości: wzrost EPS o 7% rocznie przez trzy lata i spadek P/E do 25× dają łącznie około 1,01× modelowej ceny, przed dywidendami. To ilustracja ryzyka mnożnika, nie prognoza. Nie wyliczamy ceny docelowej ani stopy zwrotu w PLN bez założeń FX, podatków i wypłat.

## 11. Ryzyka i czerwone flagi
- Wydłużenie cyklu wymiany urządzeń i osłabienie popytu konsumenckiego.
- Zmiany regulacji platform oraz zasad rozliczeń usług.
- Koncentracja łańcucha dostaw i ekspozycja geograficzna.
- Skup akcji przy wysokiej wycenie może mieć niski zwrot ekonomiczny.
- Wysoka jakość biznesu nie zapewnia automatycznie przewagi nad indeksem.

## 12. Scoring
| Kryterium | Punkty 0–3 | Powód |
| --- | ---: | --- |
| Przychody | 3 | Stabilny, choć umiarkowany wzrost |
| Zyski | 3 | Dodatni wzrost zysku netto |
| Marże | 2 | Stabilność po poprawie w B |
| Fosa | 3 | Integracja, marka i dystrybucja |
| Konkurencja | 1 | Silna rywalizacja w urządzeniach i usługach |
| Branża | 2 | Dojrzała, ale cykliczna konsumpcja |
| Zarząd i alokacja kapitału | 2 | Warunkowo zdyscyplinowany skup |
| Wycena | 0 | Wymagający mnożnik przy umiarkowanym wzroście |
| **Razem** | **16/24** | **Obserwować** |

## 13. Najważniejsze wnioski
Model pokazuje spółkę o wysokiej konwersji gotówki, lecz bez założenia gwałtownego wzrostu. Ryzyko inwestycyjne wynika głównie z zapłaconej ceny, utrzymania ekonomiki usług i cyklu wymiany urządzeń. To kontrast do kapitałochłonnej chmury Oracle oraz szybkiego wzrostu NVIDIA.

## 14. Decyzja
**OBSERWOWAC.** Trzyletnia teza wymaga wzrostu gotówki na akcję oraz utrzymania retencji klientów. Modelowy warunek przeglądu: wycena P/E poniżej 25× przy dodatnim wzroście zysku i FCF. Warunek odrzucenia tezy: dwa okresy spadku sprzedaży połączone z utratą retencji lub trwałym pogorszeniem rentowności usług. Progi służą ćwiczeniu; nie stanowią polecenia zakupu ani sprzedaży.

## 15. Braki danych i publiczny kontekst
Brak danych o retencji, marżach usług, geograficznej koncentracji, regulacjach oraz faktycznej zmianie liczby akcji. Nie przypisujemy modelowej ceny do notowań aplikacji.

[Usługi Apple](https://www.apple.com/services/) · [Relacje inwestorskie Apple](https://investor.apple.com/). Publiczne linki opisują kontekst, nie potwierdzają fikcyjnych liczb. Raport napisany od zera bez prywatnych materiałów.$apple_report$,
'{"przychody":3,"zyski":3,"marze":2,"fosa":3,"konkurencja":1,"branza":2,"zarzadIAlokacjaKapitalu":2,"wycena":0}', 'OBSERWOWAC');

DELETE FROM mf_stock_analysis;

INSERT INTO mf_stock_analysis (
  stock_id, company_name, ticker, country, industry, source_markdown,
  status, model, prompt_version, report_markdown, total_score, decision,
  metadata_json, created_at, completed_at, updated_at
)
SELECT demo_analysis_reports.stock_id, demo_analysis_reports.company_name,
       demo_analysis_reports.ticker, demo_analysis_reports.country,
       demo_analysis_reports.industry, demo_analysis_reports.source_markdown,
       'COMPLETED', 'demo-authored', 'demo-stock-analysis-v2',
       demo_analysis_reports.report_markdown,
       (SELECT SUM(value::integer) FROM jsonb_each_text(demo_analysis_reports.scores)),
       demo_analysis_reports.decision,
       jsonb_build_object(
         'synthetic', true,
         'provenance', 'Hand-authored fictional scenario; public business context only',
         'asOfDate', '2026-11-13',
         'reportMarkdown', demo_analysis_reports.report_markdown,
         'totalScore', (SELECT SUM(value::integer) FROM jsonb_each_text(demo_analysis_reports.scores)),
         'decision', demo_analysis_reports.decision,
         'hardGates', jsonb_build_object(
           'revenueGrowth', true, 'profitability', true, 'debt', true,
           'understandableRepeatableBusiness', true,
           'notOnlyFashionOrSingleAssumption', true, 'betterThanIndexThesis', true,
           'passed', true
         ),
         'scores', demo_analysis_reports.scores,
         'redFlags', jsonb_build_array('Model assumptions require verification against public issuer reports'),
         'dataGaps', jsonb_build_array('Real financial data, current valuation and company-specific disclosures are not supplied')
       ),
       '2026-11-13'::timestamp, '2026-11-13'::timestamp, '2026-11-13'::timestamp
FROM demo_analysis_reports;
