package com.acoria.cleanarchtictureexampleapp.nature.buildNature

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acoria.cleanarchtictureexampleapp.nature.*
import com.acoria.cleanarchtictureexampleapp.nature.model.IPlant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber


class BuildNatureViewModel(private val plantRepo: PlantRepository) : ViewModel() {

    //--View State--
    private var _viewStateLiveData = MutableLiveData<NatureViewState>()

    //separate property so it can be exposed as immutable
    val viewState: LiveData<NatureViewState>
        get() = _viewStateLiveData

    //store view state so it can be easily copied
    private var currentViewState =
        NatureViewState()
        set(value) {
            field = value
            _viewStateLiveData.value = value
        }

    //--View Effect--
    private val _viewEffectLiveData = MutableLiveData<NatureViewEffect>()
    val viewEffects: LiveData<NatureViewEffect>
        get() = _viewEffectLiveData

    private var searchPlantInRepoJob: Job? = null


    fun onEvent(event: NatureViewEvent) {
        Timber.d("##event $event")

        when (event) {
            is NatureViewEvent.AddPlantToFavoritesEvent -> {
                onAddPlantToFavorites()
            }
            is NatureViewEvent.SearchPlantEvent -> {
                onSearchPlant(event.searchedPlantName)
            }
        }
    }

    private fun onAddPlantToFavorites() {
        val plant = currentViewState.searchedPlantReference
        if (plant == null) {
            Timber.w("could not find searched plant reference : $plant")
            return
        }

        val adapterList = currentViewState.favoritesAdapterList

        val result: Lce<NatureResult> = if (!adapterList.contains(plant)) {
            //hand over the result so it can be added
            Lce.Content(
                NatureResult.AddToFavoriteListResult(
                    plant
                )
            )
        } else {
            Lce.Content(
                NatureResult.AddToFavoriteListResult(
                    null
                )
            )
        }
        resultToViewState(result)
        resultToViewEffect(result)
    }

    private fun onSearchPlant(searchedPlantName: String) {
        resultToViewState(Lce.Loading())

        if (searchPlantInRepoJob?.isActive == true) searchPlantInRepoJob?.cancel()

        searchPlantInRepoJob = viewModelScope.launch {
            val foundPlant = plantRepo.searchForPlant(searchedPlantName)
//            if (foundPlant == null) {
//                resultToViewEffect(Lce.Error(NatureResult.ToastResult("There is no result for '$searchedPlantName'")))
//            }
            resultToViewState(
                Lce.Content(
                    NatureResult.SearchPlantResult(
                        foundPlant
                    )
                )
            )
        }
    }

    private fun onScreenLoad() {
        resultToViewState(Lce.Loading())
    }

    private fun resultToViewEffect(result: Lce<NatureResult>) {
        Timber.d("##resultToEffect $result")

        if (result is Lce.Content && result.content is NatureResult.AddToFavoriteListResult) {
            _viewEffectLiveData.value =
                NatureViewEffect.AddedToFavoritesEffect
        }
        if (result is Lce.Error && result.error is NatureResult.ToastResult) {
            _viewEffectLiveData.value =
                NatureViewEffect.ShowToast(
                    result.error.toastMessage
                )
        }
    }

    private fun resultToViewState(result: Lce<NatureResult>) {
        Timber.d("##resultToViewState $result")

        currentViewState = when (result) {
            is Lce.Content -> {
                when (result.content) {
                    is NatureResult.SearchPlantResult -> {
                        val plant = result.content.plant
                        if(plant != null) {
                            currentViewState.copy(
                                searchedPlantName = plant.name,
                                searchedPlantMaxHeight = plant.maxHeight.toString(),
                                searchedPlantReference = plant,
                                searchedImage = plant.imageUrl
                            )
                        }else{
                            currentViewState.copy(
                                searchedPlantName = "",
                                searchedPlantMaxHeight = "",
                                searchedPlantReference = null,
                                searchedImage = ""
                            )
                        }

                    }
                    is NatureResult.AddToFavoriteListResult -> {
                        result.content.newFavoritePlant
                            ?.let {
                                val newAdapterList: MutableList<IPlant> =
                                    currentViewState.favoritesAdapterList.toMutableList()
                                newAdapterList.add(it)
                                currentViewState.copy(favoritesAdapterList = newAdapterList)
                            } ?: currentViewState.copy()
                    }
                    is NatureResult.ToastResult -> {
                        //TODO? - stupid that this has to be added as a branch :(
                        currentViewState
                    }
                }
            }
            is Lce.Loading -> {
                //TODO: show loading
                currentViewState
            }
            is Lce.Error -> {
                //TODO: error handling
                currentViewState
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        //watch out for leaks: https://medium.com/androiddevelopers/viewmodels-and-livedata-patterns-antipatterns-21efaef74a54
        //drop any callbacks to the view model from components that exist in the entire application/are scoped to it
        //e.g. a repository: If the repository is holding a reference to a callback in the ViewModel, the ViewModel will be temporarily leaked
        //TODO: not sure if this is enough:
        if (searchPlantInRepoJob?.isActive == true) searchPlantInRepoJob?.cancel()
    }
}