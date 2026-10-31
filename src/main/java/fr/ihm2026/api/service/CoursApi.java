package fr.ihm2026.api.service;

import fr.ihm2026.api.ApiClient;
import fr.ihm2026.api.dto.NiveauDto;
import fr.ihm2026.api.mock.MockBackend;
import fr.ihm2026.core.Config;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.CoursVM;
import fr.ihm2026.model.EtatNiveau;
import fr.ihm2026.model.NiveauVM;
import java.util.Arrays;
import java.util.List;

/** Niveaux et cours d'un parcours. Endpoints prévus : GET /parcours/{langage}/niveaux, /cours. */
public final class CoursApi {

    private CoursApi() {
    }

    public static List<NiveauVM> niveaux(Session.Langage langage) {
        if (Config.USE_MOCK) {
            return MockBackend.niveaux(langage);
        }
        NiveauDto[] dto = ApiClient.get("/parcours/" + langage.code() + "/niveaux", NiveauDto[].class);
        return Arrays.stream(dto).map(d -> new NiveauVM(d.rang(), d.titre(),
                EtatNiveau.valueOf(d.etat()), d.seuil(), d.note(), d.tentativesRestantes(), d.langage())).toList();
    }

    public static CoursVM cours(Session.Langage langage, int rang) {
        if (Config.USE_MOCK) {
            return MockBackend.cours(langage, rang);
        }
        throw new UnsupportedOperationException("GET /parcours/{langage}/niveaux/{rang}/cours : v0.3");
    }
}
