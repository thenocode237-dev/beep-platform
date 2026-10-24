-- Élargit la liste des usages des jetons à usage unique (lien magique, second facteur en attente).
-- Nommée « contract » parce qu'un CHECK ne s'élargit qu'en le recréant (DROP CONSTRAINT, signalé par MigrationPolicyTest).
-- Sans risque dans le même déploiement : aucune donnée n'est supprimée et l'ancien code n'écrit que des valeurs déjà admises.
ALTER TABLE one_time_tokens DROP CONSTRAINT one_time_tokens_purpose_check;
ALTER TABLE one_time_tokens ADD CONSTRAINT one_time_tokens_purpose_check
    CHECK (purpose IN ('verify_email', 'reset_password', 'magic_link', 'mfa_pending'));
