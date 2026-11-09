package ru.sportlife.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ru.sportlife.mcp.config.SportlifeProperties;
import ru.sportlife.mcp.model.Activity;
import ru.sportlife.mcp.model.Club;
import ru.sportlife.mcp.model.ClubCard;
import ru.sportlife.mcp.model.Masseur;
import ru.sportlife.mcp.model.MasseurDetail;
import ru.sportlife.mcp.model.Trainer;
import ru.sportlife.mcp.model.TrainerDetail;

class SportlifeClientTest {

    private MockRestServiceServer server;
    private SportlifeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sync.sportlifeclub.ru");
        server = MockRestServiceServer.bindTo(builder).build();
        SportlifeProperties properties = new SportlifeProperties(
                "https://sync.sportlifeclub.ru",
                "token",
                "6",
                "ios",
                "3.9.10",
                "Europe/Moscow",
                Duration.ofSeconds(5),
                Duration.ofSeconds(15));
        client = new SportlifeClient(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void getClubsReturnsPeopleCount() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://sync.sportlifeclub.ru/handh/api/v1/club")))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","list":[
                          {"id":"1","name":"Аптекарский","address":"a","people":56},
                          {"id":"6","name":"Меркурий","address":"b","people":116}]}
                        """, MediaType.APPLICATION_JSON));

        List<Club> clubs = client.getClubs();

        assertThat(clubs).hasSize(2);
        assertThat(clubs.get(1).name()).isEqualTo("Меркурий");
        assertThat(clubs.get(1).people()).isEqualTo(116);
    }

    @Test
    void getClubsReturnsEmptyListWhenListMissing() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://sync.sportlifeclub.ru/handh/api/v1/club")))
                .andRespond(withSuccess("""
                        {"code":1,"message":""}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getClubs()).isEmpty();
    }

    @Test
    void getClubCardReturnsRemainingDays() {
        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.8/club_card"))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","data":{
                          "company_name":"ООО \\"ГалаСпорт\\"",
                          "current_contracts":[{
                            "id":"1294850","name":"Полный","status":"active",
                            "start_date":"2026-08-11T00:00:00.000",
                            "finish_date":"2027-08-20T00:00:00.000",
                            "contract_remain_days":281,"remain_days":291}]}}
                        """, MediaType.APPLICATION_JSON));

        ClubCard card = client.getClubCard();

        assertThat(card.currentContracts()).hasSize(1);
        assertThat(card.currentContracts().getFirst().contractRemainDays()).isEqualTo(281);
        assertThat(card.currentContracts().getFirst().finishDate()).isEqualTo("2027-08-20T00:00:00.000");
    }

    @Test
    void getClubCardReturnsEmptyContractsWhenMissing() {
        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.8/club_card"))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","data":{"company_name":"ООО \\"ГалаСпорт\\""}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getClubCard().currentContracts()).isEmpty();
    }

    @Test
    void getActivitiesReturnsOnlyRequestedDay() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.2/schedule?type=club"))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","list":[
                          {"date":"2026-11-03T00:00:00.000","activities":[
                            {"id":"1","name":"Йога","date":"2026-11-03T10:00:00.000","dateFinish":"2026-11-03T11:00:00.000",
                             "hall":{"name":"Зал №1"},"trainer":{"name":"Иванов Иван"}}]},
                          {"date":"2026-11-04T00:00:00.000","activities":[
                            {"id":"2","name":"Теннис","date":"2026-11-04T10:00:00.000","dateFinish":"2026-11-04T11:00:00.000",
                             "hall":{"name":"Зал №2"},"trainer":{"name":"Петров Петр"}}]}]}
                        """, MediaType.APPLICATION_JSON));

        List<Activity> activities = client.getActivities(today);

        assertThat(activities).hasSize(1);
        assertThat(activities.getFirst().name()).isEqualTo("Йога");
        assertThat(activities.getFirst().hallName()).isEqualTo("Зал №1");
        assertThat(activities.getFirst().trainerName()).isEqualTo("Иванов Иван");
    }

    @Test
    void getActivitiesReturnsEmptyWhenNoActivitiesForDay() {
        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.2/schedule?type=club"))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","list":[
                          {"date":"2026-11-04T00:00:00.000","activities":[
                            {"id":"2","name":"Теннис","date":"2026-11-04T10:00:00.000","dateFinish":"2026-11-04T11:00:00.000",
                             "hall":{"name":"Зал №2"},"trainer":{"name":"Петров Петр"}}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getActivities(LocalDate.of(2026, 11, 3))).isEmpty();
    }

    @Test
    void getMasseursReturnsSpecialists() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://sync.sportlifeclub.ru/handh/api/v1.7/masseurs")))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","total":1,"list":[
                          {"id":"12475","name":"Смирнова Марина","club_name":"Меркурий",
                           "hall_name":"Кабинет по уходу за телом","date":"2026-11-04T00:00:00.000"}]}
                        """, MediaType.APPLICATION_JSON));

        List<Masseur> masseurs = client.getMasseurs();

        assertThat(masseurs).hasSize(1);
        assertThat(masseurs.getFirst().name()).isEqualTo("Смирнова Марина");
        assertThat(masseurs.getFirst().availableDate()).isEqualTo("2026-11-04T00:00:00.000");
    }

    @Test
    void getMasseurDetailReturnsSpecialistWithSchedule() {
        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.2/masseur?masseur_id=9836"))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","description":"Специалист по уходу за телом","price":"1900",
                         "resume":{"specialization":"Классический массаж","education":"Школа мастеров","expirience":"Стаж с 2018 года"},
                         "schedule":[{"date":"2026-11-09T00:00:00.000","schedule_club":[
                           {"club_name":"Меркурий","intervals":[
                             {"id":"S74964T800","name":"8:00","available":true},
                             {"id":"S74964T1100","name":"11:00","available":false}]}]}],
                         "id":"9836","name":"Старостина Елена","avatar":"/trainer/avatar/X","is_favorite":true}
                        """, MediaType.APPLICATION_JSON));

        MasseurDetail detail = client.getMasseurDetail("9836");

        assertThat(detail.id()).isEqualTo("9836");
        assertThat(detail.name()).isEqualTo("Старостина Елена");
        assertThat(detail.price()).isEqualTo("1900");
        assertThat(detail.resume().experience()).isEqualTo("Стаж с 2018 года");
        assertThat(detail.schedule()).hasSize(1);
        assertThat(detail.schedule().getFirst().date()).isEqualTo("2026-11-09T00:00:00.000");
        assertThat(detail.schedule().getFirst().clubs().getFirst().clubName()).isEqualTo("Меркурий");
        assertThat(detail.schedule().getFirst().clubs().getFirst().slots().getFirst().time()).isEqualTo("8:00");
        assertThat(detail.schedule().getFirst().clubs().getFirst().slots().getFirst().available()).isTrue();
    }

    @Test
    void getTrainersReturnsPersonalTrainers() {
        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.5/trainer?limit=30&offset=0"))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","total":1,"list":[
                          {"id":"214","name":"Максимчук Наталья","main_speciality":"Силовой тренинг",
                           "price":3800,"rating_fractional":"4,4","type":"both"}]}
                        """, MediaType.APPLICATION_JSON));

        List<Trainer> trainers = client.getTrainers();

        assertThat(trainers).hasSize(1);
        assertThat(trainers.getFirst().id()).isEqualTo("214");
        assertThat(trainers.getFirst().name()).isEqualTo("Максимчук Наталья");
        assertThat(trainers.getFirst().price()).isEqualTo(3800);
        assertThat(trainers.getFirst().rating()).isEqualTo("4,4");
    }

    @Test
    void getTrainerReturnsCardWithClubsAndActivities() {
        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.5/trainer/214"))
                .andRespond(withSuccess("""
                        {"code":1,"message":"","data":{
                          "cvSpeciality":"Силовой тренинг","cvEducation":"Колледж","cvExperience":"с 2005 года",
                          "price":3800,"rating_fractional":"4,4","type":"both",
                          "id":"214","name":"Максимчук Наталья",
                          "clubs":[{"id":"6","name":"Меркурий","address":"a"}],
                          "nearest_activities":[{"id":5566558,"name":"Круговая","date":"2026-11-09T08:00:00.000",
                            "dateFinish":"2026-11-09T09:00:00.000",
                            "hall":{"id":76,"name":"Круговые тренировки","club":{"id":6,"name":"Меркурий"}}}]}}
                        """, MediaType.APPLICATION_JSON));

        TrainerDetail detail = client.getTrainer("214");

        assertThat(detail.id()).isEqualTo("214");
        assertThat(detail.speciality()).isEqualTo("Силовой тренинг");
        assertThat(detail.clubs()).containsExactly("Меркурий");
        assertThat(detail.activities()).hasSize(1);
        assertThat(detail.activities().getFirst().name()).isEqualTo("Круговая");
        assertThat(detail.activities().getFirst().hall()).isEqualTo("Круговые тренировки");
        assertThat(detail.activities().getFirst().club()).isEqualTo("Меркурий");
    }

    @Test
    void apiErrorCodeIsReported() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://sync.sportlifeclub.ru/handh/api/v1/club")))
                .andRespond(withSuccess("""
                        {"code":0,"message":"token expired","list":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getClubs())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token expired");
    }
}
