package fr.ihm2026.api.ia;

import fr.ihm2026.core.Session;
import java.util.List;

/** Contexte pédagogique et trois échanges récents maximum ; aucun identifiant de compte. */
public record ContexteIa(int rangNiveau, Session.Langage langage, String question, String code,
                         List<Tour> historique) {
    public static final int TOURS_MAX=3;
    public record Tour(String question,String reponse) {
        public Tour {
            if(question==null || reponse==null || question.length()>2000 || reponse.length()>2400)
                throw new IllegalArgumentException("Échange IA invalide ou trop long.");
        }
    }
    public ContexteIa(int rangNiveau,Session.Langage langage,String question,String code) {
        this(rangNiveau,langage,question,code,List.of());
    }
    public ContexteIa {
        question=question==null?"":question.strip();code=code==null?"":code;
        historique=historique==null?List.of():List.copyOf(historique);
        if(historique.size()>TOURS_MAX)throw new IllegalArgumentException("Historique IA trop long.");
    }
}
